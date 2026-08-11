package ai.univs.match.application.service;

import ai.univs.match.application.enums.DescriptorSpec;
import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyCandidateResult;
import ai.univs.match.application.result.IdentifyCandidatesResult;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityByDistance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IdentifyService")
@ExtendWith(MockitoExtension.class)
class IdentifyServiceTest {

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private DescriptorCustomRepository descriptorCustomRepository;

    @InjectMocks
    private IdentifyService identifyService;

    private Branch branch;
    private DescriptorDetail descriptorDetail;

    @BeforeEach
    void setUp() {
        branch = Branch.builder()
                .id(1L)
                .branchName("testBranch")
                .build();

        descriptorDetail = DescriptorDetail.from(createBase64Descriptor(59));
    }

    @Nested
    @DisplayName("identify")
    class Identify {

        @Test
        @DisplayName("브랜치에 매칭 대상이 없으면 EMPTY_GALLERY 예외를 던진다")
        void whenNoDescriptorsInBranch_thenThrowEmptyGallery() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(0);

            assertThatThrownBy(() -> identifyService.identify(branch, descriptorDetail))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));
        }

        @Test
        @DisplayName("매칭 대상이 없으면 oneToManyMatch를 호출하지 않는다")
        void whenNoDescriptorsInBranch_thenNeverCallsOneToManyMatch() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(0);

            assertThatThrownBy(() -> identifyService.identify(branch, descriptorDetail));

            verify(descriptorCustomRepository, never()).oneToManyMatch(any(), any(), anyInt());
        }

        @Test
        @DisplayName("매칭 대상이 있으면 branchId와 버전 512로 oneToManyMatch를 호출한다")
        void whenDescriptorsExist_thenCallsOneToManyMatchWithBranchIdAndVersion512() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(3);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", 1.0));

            identifyService.identify(branch, descriptorDetail);

            verify(descriptorCustomRepository).oneToManyMatch(eq(1L), any(byte[].class), eq(512));
        }

        @Test
        @DisplayName("oneToManyMatch 결과의 faceId를 IdentifyResult에 담아 반환한다")
        void whenMatchFound_thenReturnsFaceIdFromProjection() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", 1.0));

            IdentifyResult result = identifyService.identify(branch, descriptorDetail);

            assertThat(result.faceId()).isEqualTo("face-001");
        }

        @Test
        @DisplayName("distance가 0 이하이면 유사도 1.00000을 반환한다")
        void whenDistanceIsZero_thenReturnMaxSimilarity() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", 0.0));

            IdentifyResult result = identifyService.identify(branch, descriptorDetail);

            assertThat(result.similarity()).isEqualTo("1.00000");
        }

        @Test
        @DisplayName("distance를 DescriptorSpec의 Platt scaling으로 변환한 유사도를 반환한다")
        void whenMatchFound_thenSimilarityIsConvertedFromDistance() {
            double distance = 1.0;
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", distance));

            IdentifyResult result = identifyService.identify(branch, descriptorDetail);

            String expectedSimilarity = getSimilarityByDistance(distance, DescriptorSpec.VERSION_59);
            assertThat(result.similarity()).isEqualTo(expectedSimilarity);
        }

        @Test
        @DisplayName("descriptorSpec 버전에 맞는 카운트 쿼리를 실행한다")
        void whenCalled_thenCountsWithCorrectVersion() {
            DescriptorDetail detail60 = DescriptorDetail.from(createBase64Descriptor(60));
            Branch branch2 = Branch.builder().id(2L).branchName("branch2").build();

            when(descriptorRepository.countByBranchAndDescriptorVersion(branch2, 60)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(2L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-002", 0.5));

            identifyService.identify(branch2, detail60);

            verify(descriptorRepository).countByBranchAndDescriptorVersion(branch2, 60);
        }
    }

    /**
     * UG-314: 상위 k건 조회.
     *
     * <p>여기서 지키는 계약은 두 가지다 — <b>유사도 계산 경로가 {@code identify} 와 같을 것</b>,
     * 그리고 <b>임계치로 자르지 않을 것</b>. 전자가 어긋나면 같은 얼굴에 두 API 가 다른 점수를
     * 내고, 후자를 여기서 하면 판정이 face-service 와 이원화된다.
     */
    @Nested
    @DisplayName("identifyCandidates")
    class IdentifyCandidates {

        @Test
        @DisplayName("브랜치에 매칭 대상이 없으면 EMPTY_GALLERY 예외를 던진다")
        void 빈_갤러리() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(0);

            assertThatThrownBy(() -> identifyService.identifyCandidates(branch, descriptorDetail, 5))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));

            verify(descriptorCustomRepository, never())
                    .oneToManyMatchTopK(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("요청받은 maxCandidates 를 그대로 쿼리에 넘긴다")
        void 건수를_그대로_넘긴다() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(30);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), eq(7)))
                    .thenReturn(List.of());

            identifyService.identifyCandidates(branch, descriptorDetail, 7);

            // 여기서 건수를 깎거나 상수로 바꾸면 클라이언트가 지정한 값이 조용히 무시된다.
            verify(descriptorCustomRepository).oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), eq(7));
        }

        @Test
        @DisplayName("쿼리가 준 순서를 그대로 유지한다 — 정렬은 DB 가 이미 했다")
        void 순서를_유지한다() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(3);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), anyInt()))
                    .thenReturn(List.of(
                            new MatchResultProjection("face-a", 0.8),
                            new MatchResultProjection("face-b", 1.1),
                            new MatchResultProjection("face-c", 1.4)));

            IdentifyCandidatesResult result = identifyService.identifyCandidates(branch, descriptorDetail, 3);

            assertThat(result.candidates())
                    .extracting(IdentifyCandidateResult::faceId)
                    .containsExactly("face-a", "face-b", "face-c");
        }

        @Test
        @DisplayName("거리가 가까울수록 유사도가 높다 — 목록은 유사도 내림차순이 된다")
        void 유사도_내림차순() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(3);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), anyInt()))
                    .thenReturn(List.of(
                            new MatchResultProjection("face-a", 0.8),
                            new MatchResultProjection("face-b", 1.1),
                            new MatchResultProjection("face-c", 1.4)));

            IdentifyCandidatesResult result = identifyService.identifyCandidates(branch, descriptorDetail, 3);

            // 거리 오름차순 = 유사도 내림차순. 이 동치가 깨지면 face-service 의 '앞에서부터
            // 자르기' 가 임계치를 넘는 후보를 빠뜨린다 (SimilarityMonotonicityTest 참고).
            assertThat(result.candidates())
                    .extracting(candidate -> new java.math.BigDecimal(candidate.similarity()))
                    .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }

        @Test
        @DisplayName("유사도는 identify 와 같은 계산 경로를 쓴다")
        void identify와_같은_유사도() {
            double distance = 1.0;
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), anyInt()))
                    .thenReturn(List.of(new MatchResultProjection("face-001", distance)));

            IdentifyCandidatesResult result = identifyService.identifyCandidates(branch, descriptorDetail, 1);

            assertThat(result.candidates()).hasSize(1);
            assertThat(result.candidates().getFirst().similarity())
                    .as("기존 1:N 과 다른 점수를 내면 같은 얼굴이 API 에 따라 다르게 판정된다")
                    .isEqualTo(getSimilarityByDistance(distance, DescriptorSpec.VERSION_59));
        }

        @Test
        @DisplayName("한 건도 없으면 빈 목록이다 — 예외가 아니다")
        void 후보_0명() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(5);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), anyInt()))
                    .thenReturn(List.of());

            IdentifyCandidatesResult result = identifyService.identifyCandidates(branch, descriptorDetail, 5);

            assertThat(result.candidates()).isEmpty();
        }

        @Test
        @DisplayName("distance 가 0 이하이면 유사도 1.00000 이다 — identify 와 같다")
        void 거리_0() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(1L), any(byte[].class), eq(512), anyInt()))
                    .thenReturn(List.of(new MatchResultProjection("face-001", 0.0)));

            IdentifyCandidatesResult result = identifyService.identifyCandidates(branch, descriptorDetail, 1);

            assertThat(result.candidates().getFirst().similarity()).isEqualTo("1.00000");
        }

        @Test
        @DisplayName("descriptorSpec 버전에 맞는 카운트 쿼리를 실행한다")
        void 버전별_카운트() {
            DescriptorDetail detail60 = DescriptorDetail.from(createBase64Descriptor(60));
            Branch branch2 = Branch.builder().id(2L).branchName("branch2").build();

            when(descriptorRepository.countByBranchAndDescriptorVersion(branch2, 60)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatchTopK(eq(2L), any(byte[].class), eq(512), anyInt()))
                    .thenReturn(List.of(new MatchResultProjection("face-002", 0.5)));

            identifyService.identifyCandidates(branch2, detail60, 3);

            verify(descriptorRepository).countByBranchAndDescriptorVersion(branch2, 60);
        }
    }

    // 테스트용 유효한 Base64 descriptor 생성 (8 bytes header + 512 bytes body)
    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
