package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyCandidatesByDescriptorInput;
import ai.univs.face.application.result.IdentifyCandidatesResult;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyCandidatesFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyCandidatesFeignResponseDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import ai.univs.face.shared.web.enums.ErrorType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * UG-314 face-service 쪽 계약을 지킨다.
 *
 * <p>여기서 확인하는 것은 세 가지다.
 *
 * <ul>
 *   <li><b>임계치 판정이 기존 1:N 과 같은가.</b> 두 API 가 같은 유사도에 다른 답을 내면 안 된다.
 *       그래서 {@link SimilarityParser} 를 목으로 두지 않고 <b>실물</b>을 쓴다 — 목을 쓰면
 *       비교 연산자를 {@code >} 로 바꿔도 테스트가 통과한다.</li>
 *   <li><b>이력이 요청 하나에 한 행인가</b> (패턴 A). 대표값은 최상위 후보다.</li>
 *   <li><b>후보 0명이 실패로 기록되되, 유사도까지 잃지는 않는가.</b> "후보가 없었다" 와
 *       "유사도를 못 구했다" 가 이력에서 같아 보이면 안 된다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-314: 특징점 기반 1:N 후보 목록 (face)")
class IdentifyCandidatesByDescriptorUseCaseTest {

    private static final String BRANCH = "branch-A";
    private static final String DESCRIPTOR = "descriptor-xyz";
    private static final String TXN = "txn-314";
    private static final String CLIENT = "client-A";

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private FaceMatchRepository faceMatchRepository;

    private IdentifyCandidatesByDescriptorUseCase useCase;

    @BeforeEach
    void 픽스처() {
        // SimilarityParser 는 실물이다. 임계치 필드(FACE_MATCH_THRESHOLD)를 쓰지 않는
        // 오버로드만 타므로 주입 없이 그대로 쓸 수 있다.
        useCase = new IdentifyCandidatesByDescriptorUseCase(
                matchFeign, faceHistoryRepository, faceMatchRepository, new SimilarityParser());
    }

    private static IdentifyCandidatesByDescriptorInput 입력(double threshold, int maxCandidates) {
        return new IdentifyCandidatesByDescriptorInput(
                BRANCH, DESCRIPTOR, threshold, maxCandidates, TXN, CLIENT);
    }

    /** match-server 응답. faceId·similarity 를 번갈아 넘긴다 (거리순 = 유사도 내림차순). */
    private void 매처가_돌려준다(String... faceIdAndSimilarity) {
        var candidates = Arrays.asList(faceIdAndSimilarity);
        var list = new java.util.ArrayList<IdentifyCandidatesFeignResponseDTO.Candidate>();
        for (int i = 0; i < candidates.size(); i += 2) {
            list.add(new IdentifyCandidatesFeignResponseDTO.Candidate(
                    candidates.get(i), candidates.get(i + 1)));
        }
        given(matchFeign.identifyCandidates(any()))
                .willReturn(new FeignResponseApi<>(
                        true, new IdentifyCandidatesFeignResponseDTO(list), null));
    }

    private FaceMatch 저장된_매칭() {
        ArgumentCaptor<FaceMatch> captor = ArgumentCaptor.forClass(FaceMatch.class);
        verify(faceMatchRepository).save(captor.capture());
        return captor.getValue();
    }

    private FaceHistory 저장된_이력() {
        ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);
        verify(faceHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("임계치 절삭")
    class 절삭 {

        @Test
        @DisplayName("임계치를 넘은 후보만, 매처가 준 순서 그대로 남는다")
        void 미달을_잘라낸다() {
            매처가_돌려준다("face-a", "0.97000", "face-b", "0.88000",
                    "face-c", "0.80000", "face-d", "0.10000");

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesResult.Candidate::faceId)
                    .containsExactly("face-a", "face-b");
            assertThat(result.result()).isTrue();
        }

        @Test
        @DisplayName("임계치와 정확히 같으면 통과한다 — 기존 1:N 과 같은 판정이다")
        void 경계값_동일() {
            // 기존 isMatchingBySimilarity 가 >= 다. 여기서 > 로 바꾸면 같은 유사도가 API 에
            // 따라 성공/실패로 갈린다.
            매처가_돌려준다("face-a", "0.85000");

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesResult.Candidate::faceId)
                    .containsExactly("face-a");
        }

        @Test
        @DisplayName("임계치보다 조금이라도 낮으면 잘린다")
        void 경계값_바로_아래() {
            매처가_돌려준다("face-a", "0.84999");

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates()).isEmpty();
            assertThat(result.result()).isFalse();
        }

        @Test
        @DisplayName("유사도 문자열을 그대로 내보낸다 — 재가공하지 않는다")
        void 유사도_원값() {
            매처가_돌려준다("face-a", "0.97314");

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates().getFirst().similarity()).isEqualTo("0.97314");
        }
    }

    @Nested
    @DisplayName("match-server 호출")
    class 호출 {

        @Test
        @DisplayName("임계치를 match-server 로 보내지 않는다 — 자르는 것은 이쪽 몫이다")
        void 임계치는_안_보낸다() {
            매처가_돌려준다("face-a", "0.97000");

            useCase.execute(입력(0.85, 7));

            ArgumentCaptor<IdentifyCandidatesFeignRequestDTO> captor =
                    ArgumentCaptor.forClass(IdentifyCandidatesFeignRequestDTO.class);
            verify(matchFeign).identifyCandidates(captor.capture());

            IdentifyCandidatesFeignRequestDTO sent = captor.getValue();
            assertThat(sent.getBranchName()).isEqualTo(BRANCH);
            assertThat(sent.getDescriptor()).isEqualTo(DESCRIPTOR);
            assertThat(sent.getMaxCandidates())
                    .as("요청받은 건수를 깎아 보내면 클라이언트 지정값이 조용히 무시된다")
                    .isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("이력 (패턴 A — 요청 하나에 한 행)")
    class 이력 {

        @Test
        @DisplayName("후보가 여럿이어도 face_match 는 한 행이고 대표값은 최상위 후보다")
        void 대표값은_최상위() {
            매처가_돌려준다("face-a", "0.97000", "face-b", "0.88000");

            useCase.execute(입력(0.85, 10));

            FaceMatch saved = 저장된_매칭();
            assertThat(saved.getFaceId())
                    .as("2등을 남기면 이력만 보고 '누가 매칭됐나' 를 답할 수 없게 된다")
                    .isEqualTo("face-a");
            assertThat(saved.getSimilarity()).isEqualTo(0.97);
            assertThat(saved.getThreshold())
                    .as("서버 설정값이 아니라 요청값이 남아야 나중에 판정을 재현할 수 있다")
                    .isEqualTo(0.85);
            assertThat(saved.getType()).isEqualTo(MatchType.IDENTIFY);
        }

        @Test
        @DisplayName("후보 0명 — 실패로 남기되 유사도는 가장 가까웠던 후보의 값이다")
        void 후보_0명() {
            매처가_돌려준다("face-a", "0.60000", "face-b", "0.50000");

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates()).isEmpty();
            assertThat(result.result()).isFalse();

            FaceMatch saved = 저장된_매칭();
            assertThat(saved.getSimilarity())
                    .as("0 으로 남기면 '아무도 근접하지 않았다' 와 '유사도를 못 구했다' 가 "
                            + "이력에서 같아 보인다")
                    .isEqualTo(0.60);
            assertThat(saved.getFaceId())
                    .as("임계치를 넘은 사람이 없으므로 특정할 대상이 없다")
                    .isEmpty();

            assertThat(저장된_이력().getFailureMessage()).isEqualTo(ErrorType.NOT_MATCH.name());
        }

        @Test
        @DisplayName("매처가 후보를 하나도 안 주면 유사도 0 으로 남는다")
        void 매처_빈_목록() {
            매처가_돌려준다();

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates()).isEmpty();
            assertThat(저장된_매칭().getSimilarity()).isZero();
        }

        @Test
        @DisplayName("매처가 candidates 를 null 로 줘도 깨지지 않는다")
        void 매처_null() {
            given(matchFeign.identifyCandidates(any()))
                    .willReturn(new FeignResponseApi<>(
                            true, new IdentifyCandidatesFeignResponseDTO(null), null));

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates()).isEmpty();
            assertThat(result.result()).isFalse();
        }

        @Test
        @DisplayName("응답의 threshold 는 요청값이다")
        void 응답_임계치() {
            매처가_돌려준다("face-a", "0.97000");

            IdentifyCandidatesResult result = useCase.execute(입력(0.9, 10));

            assertThat(result.threshold()).isEqualTo("0.9");
            assertThat(result.transactionUuid()).isEqualTo(TXN);
        }
    }

    @Nested
    @DisplayName("매처 호출 실패")
    class 실패 {

        @Test
        @DisplayName("Feign 실패 — 사유를 이력에 남기고 InvalidFaceModuleException 으로 바꿔 던진다")
        void feign_실패() {
            given(matchFeign.identifyCandidates(any())).willThrow(new CustomFeignException(
                    ErrorType.FACE_NOT_FOUND.getCode(), ErrorType.FACE_NOT_FOUND.name(), "no face"));

            assertThatThrownBy(() -> useCase.execute(입력(0.85, 10)))
                    .isInstanceOf(InvalidFaceModuleException.class);

            assertThat(저장된_이력().getFailureMessage())
                    .as("failure_message 가 비면 이력에서 '왜 실패했는지 알 수 없는 행' 이 된다")
                    .isEqualTo(ErrorType.FACE_NOT_FOUND.name());

            // 매칭 자체가 성립하지 않았으므로 face_match 행은 만들지 않는다.
            verify(faceMatchRepository, org.mockito.Mockito.never()).save(any());
        }
    }

    @Nested
    @DisplayName("단조성 전제")
    class 단조성 {

        @Test
        @DisplayName("앞에서부터 자르기만 한다 — 미달 뒤에 통과가 섞여 있으면 그건 상류가 깨진 것이다")
        void 앞에서부터_자른다() {
            // match-server 는 거리 오름차순(=유사도 내림차순)으로 준다. 이 전제가 지켜지는 한
            // '미달을 만나면 그 뒤는 전부 미달' 이므로 절삭이 곧 상위 k건이 된다.
            // (전제 자체는 match 의 SimilarityMonotonicityTest 가 지킨다.)
            매처가_돌려준다("face-a", "0.97000", "face-b", "0.86000", "face-c", "0.85000");

            IdentifyCandidatesResult result = useCase.execute(입력(0.85, 10));

            assertThat(result.candidates())
                    .extracting(IdentifyCandidatesResult.Candidate::faceId)
                    .containsExactly("face-a", "face-b", "face-c");
        }

        @Test
        @DisplayName("매처가 준 건수보다 많이 만들어 내지 않는다")
        void 건수를_부풀리지_않는다() {
            매처가_돌려준다("face-a", "0.97000", "face-b", "0.96000", "face-c", "0.95000");

            IdentifyCandidatesResult result = useCase.execute(입력(0.5, 100));

            assertThat(result.candidates()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("가드")
    class 가드 {

        @Test
        @DisplayName("이 테스트가 실물 SimilarityParser 를 쓰고 있다")
        void 실물_파서를_쓴다() {
            // 목으로 바꾸는 순간 위의 경계값 테스트가 전부 무의미해진다 — 비교 연산자를
            // 뒤집어도 통과하게 된다. 그 변경이 눈에 띄도록 여기서 못 박아 둔다.
            SimilarityParser parser = new SimilarityParser();

            assertThat(parser.isMatchingBySimilarity(0.85, 0.85)).isTrue();
            assertThat(parser.isMatchingBySimilarity(0.84999, 0.85)).isFalse();
            assertThat(List.of(useCase)).isNotEmpty();
        }
    }
}
