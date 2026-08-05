package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyByDescriptorInput;
import ai.univs.face.application.input.RegisterByDescriptorInput;
import ai.univs.face.application.result.IdentifyResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.RegisterV2FeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.feign.dto.FeignResponseApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * UG-279: descriptor 기반 등록·1:N UseCase.
 *
 * <p>반박 리뷰에서 <b>"유사도 임계치 판정을 무조건 true 로 바꾸는" 뮤테이션이 살아남았다.</b>
 * 그건 1:N 매칭을 항상 성공으로 만드는 인증 우회인데, 당시 이 두 UseCase 에는 테스트가 0줄이었다.
 * 컨트롤러 테스트는 UseCase 를 목으로 두므로 비즈니스 로직을 전혀 보증하지 않는다.
 *
 * <p>또한 이 두 UseCase 는 {@link ExtractService} 를 <b>주입하지 않는 것 자체가 계약</b>이다 —
 * descriptor 가 있다는 것은 추출·라이브니스가 끝났다는 뜻이므로 재실행할 대상이 없고, 의존성이
 * 없으면 실수로도 호출될 수 없다. 그 계약을 리플렉션으로 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-279: descriptor 기반 등록·1:N UseCase")
class DescriptorUseCaseTest {

    private static final String BRANCH = "branch-A";
    private static final String DESCRIPTOR = "descriptor-A";
    private static final String TXN = "txn-001";
    private static final String CLIENT = "client-A";

    @Nested
    @DisplayName("등록")
    class 등록 {

        @Mock private MatchFeign matchFeign;
        @Mock private FaceHistoryRepository faceHistoryRepository;

        @InjectMocks private RegisterByDescriptorUseCase useCase;

        private final RegisterByDescriptorInput input =
                new RegisterByDescriptorInput(BRANCH, DESCRIPTOR, TXN, CLIENT);

        @Test
        @DisplayName("성공 — faceId 없는 register(v2) 경로로 descriptor 를 그대로 넘기고 성공 이력을 남긴다")
        void 성공() {
            given(matchFeign.register(any(RegisterV2FeignRequestDTO.class)))
                    .willReturn(new FeignResponseApi<>(
                            true, new MatchFeignResponseDTO(BRANCH, "issued-face-id"), null));

            RegisterResult result = useCase.execute(input);

            assertThat(result.branchName()).isEqualTo(BRANCH);
            assertThat(result.faceId()).isEqualTo("issued-face-id");
            assertThat(result.transactionUuid()).isEqualTo(TXN);

            ArgumentCaptor<RegisterV2FeignRequestDTO> request =
                    ArgumentCaptor.forClass(RegisterV2FeignRequestDTO.class);
            verify(matchFeign).register(request.capture());
            assertThat(request.getValue().getDescriptor())
                    .as("클라이언트가 준 descriptor 가 가공 없이 매처로 전달돼야 한다")
                    .isEqualTo(DESCRIPTOR);
            assertThat(request.getValue().getBranchName()).isEqualTo(BRANCH);

            FaceHistory history = capturedHistory(faceHistoryRepository);
            assertThat(history.getType()).isEqualTo(ActionType.ADD);
            assertThat(history.isResult()).isTrue();
            assertThat(history.getFaceId()).isEqualTo("issued-face-id");
            assertThat(history.isCheckLiveness())
                    .as("descriptor 경로에는 검사할 이미지가 없다")
                    .isFalse();
            assertThat(history.isCheckMultiFace()).isFalse();
        }

        @Test
        @DisplayName("매처 실패 — 실패 이력을 남기고 InvalidFaceModuleException 으로 변환한다")
        void 매처_실패() {
            given(matchFeign.register(any(RegisterV2FeignRequestDTO.class)))
                    .willThrow(new CustomFeignException("E006", "SERVER_ERROR", "매처 오류"));

            assertThatThrownBy(() -> useCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("SERVER_ERROR");

            assertThat(capturedHistory(faceHistoryRepository).getFailureMessage())
                    .isEqualTo("SERVER_ERROR");
        }

        @Test
        @DisplayName("ExtractService 를 주입받지 않는다 — 라이브니스가 구조적으로 실행 불가")
        void 추출_의존성_없음() {
            assertNoExtractServiceDependency(RegisterByDescriptorUseCase.class);
        }
    }

    @Nested
    @DisplayName("1:N 매칭")
    class 매칭 {

        @Mock private MatchFeign matchFeign;
        @Mock private FaceHistoryRepository faceHistoryRepository;
        @Mock private FaceMatchRepository faceMatchRepository;
        @Mock private SimilarityParser similarityParser;

        @InjectMocks private IdentifyByDescriptorUseCase useCase;

        private final IdentifyByDescriptorInput input =
                new IdentifyByDescriptorInput(BRANCH, DESCRIPTOR, TXN, CLIENT);

        @BeforeEach
        void 임계치() {
            lenient().when(similarityParser.getThreshold()).thenReturn(0.85);
            lenient().when(similarityParser.getThresholdString()).thenReturn("0.85");
        }

        private void 매처응답(String similarity, String faceId) {
            given(matchFeign.identify(any(IdentifyFeignRequestDTO.class)))
                    .willReturn(new FeignResponseApi<>(
                            true, new IdentifyFeignResponseDTO(faceId, similarity), null));
        }

        @Test
        @DisplayName("유사도 >= 임계치 → 성공, faceId 반환 및 FaceMatch 저장")
        void 임계치_이상() {
            매처응답("0.90", "matched-face-id");
            given(similarityParser.parseDoubleSimilarity("0.90")).willReturn(0.90);
            given(similarityParser.isMatchingBySimilarity(0.90)).willReturn(true);

            IdentifyResult result = useCase.execute(input);

            assertThat(result.result()).isTrue();
            assertThat(result.faceId()).isEqualTo("matched-face-id");
            assertThat(result.similarity()).isEqualTo("0.90");
            assertThat(result.threshold()).isEqualTo("0.85");

            ArgumentCaptor<FaceMatch> match = ArgumentCaptor.forClass(FaceMatch.class);
            verify(faceMatchRepository).save(match.capture());
            assertThat(match.getValue().getType()).isEqualTo(MatchType.IDENTIFY);

            FaceHistory history = capturedHistory(faceHistoryRepository);
            assertThat(history.getType()).isEqualTo(ActionType.MATCH);
            assertThat(history.isResult()).isTrue();
            assertThat(history.isCheckLiveness()).isFalse();
        }

        @Test
        @DisplayName("유사도 < 임계치 → 실패. 임계치 판정을 우회하면 인증이 무력화된다")
        void 임계치_미달() {
            매처응답("0.60", "matched-face-id");
            given(similarityParser.parseDoubleSimilarity("0.60")).willReturn(0.60);
            given(similarityParser.isMatchingBySimilarity(0.60)).willReturn(false);

            IdentifyResult result = useCase.execute(input);

            assertThat(result.result())
                    .as("임계치 미달인데 성공이 반환되면 1:N 매칭이 상시 통과하는 인증 우회가 된다")
                    .isFalse();
            assertThat(result.faceId())
                    .as("실패 시 매칭된 faceId 를 노출해서는 안 된다")
                    .isEmpty();

            assertThat(capturedHistory(faceHistoryRepository).getFailureMessage())
                    .isEqualTo("NOT_MATCH");
        }

        @Test
        @DisplayName("descriptor 는 가공 없이 매처로 전달된다")
        void descriptor_전달() {
            매처응답("0.90", "matched-face-id");
            given(similarityParser.parseDoubleSimilarity("0.90")).willReturn(0.90);
            given(similarityParser.isMatchingBySimilarity(0.90)).willReturn(true);

            useCase.execute(input);

            ArgumentCaptor<IdentifyFeignRequestDTO> request =
                    ArgumentCaptor.forClass(IdentifyFeignRequestDTO.class);
            verify(matchFeign).identify(request.capture());
            assertThat(request.getValue().getDescriptor()).isEqualTo(DESCRIPTOR);
            assertThat(request.getValue().getBranchName()).isEqualTo(BRANCH);
        }

        @Test
        @DisplayName("매처 실패 — 실패 이력을 남기고 InvalidFaceModuleException 으로 변환한다")
        void 매처_실패() {
            given(matchFeign.identify(any(IdentifyFeignRequestDTO.class)))
                    .willThrow(new CustomFeignException("E006", "EMPTY_GALLERY", "갤러리 비어 있음"));

            assertThatThrownBy(() -> useCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("EMPTY_GALLERY");

            assertThat(capturedHistory(faceHistoryRepository).getFailureMessage())
                    .isEqualTo("EMPTY_GALLERY");
        }

        @Test
        @DisplayName("ExtractService 를 주입받지 않는다 — 라이브니스가 구조적으로 실행 불가")
        void 추출_의존성_없음() {
            assertNoExtractServiceDependency(IdentifyByDescriptorUseCase.class);
        }
    }

    private static FaceHistory capturedHistory(FaceHistoryRepository repository) {
        ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertNoExtractServiceDependency(Class<?> useCase) {
        assertThat(Arrays.stream(useCase.getDeclaredFields()).map(Field::getType))
                .as("%s 가 ExtractService 를 주입받으면 라이브니스·다중 얼굴 검사가 실행될 수 있다. "
                        + "descriptor 경로는 그 단계가 이미 끝난 입력을 받는다", useCase.getSimpleName())
                .doesNotContain(ExtractService.class);
    }
}
