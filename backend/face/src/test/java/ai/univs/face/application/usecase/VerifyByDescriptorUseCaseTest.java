package ai.univs.face.application.usecase;

import ai.univs.face.application.input.VerifyByDescriptorInput;
import ai.univs.face.application.result.VerifyByDescriptorResult;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.VerifyFeignResponseDTO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerifyByDescriptorUseCaseTest {

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private FaceMatchRepository faceMatchRepository;
    @Mock private SimilarityParser similarityParser;

    @InjectMocks
    private VerifyByDescriptorUseCase verifyByDescriptorUseCase;

    private static final String TXN    = "txn-001";
    private static final String CLIENT = "client-A";

    private VerifyByDescriptorInput input;

    @BeforeEach
    void setUp() {
        input = new VerifyByDescriptorInput("descriptor-A", "descriptor-B", TXN, CLIENT);
        lenient().when(similarityParser.getThreshold()).thenReturn(0.85);
        lenient().when(similarityParser.getThresholdString()).thenReturn("0.85");
    }

    private FeignResponseApi<VerifyFeignResponseDTO> verifyResponse(String similarity) {
        return new FeignResponseApi<>(true, new VerifyFeignResponseDTO(similarity), null);
    }

    @Nested
    @DisplayName("유사도 임계치 기반 결과 분기")
    class SimilarityBranching {

        @Test
        @DisplayName("유사도 >= 임계치 → result=true 반환, FaceMatch 저장")
        void execute_whenSimilarityAboveThreshold_returnsSuccessResult() {
            given(matchFeign.verifyByDescriptor(any())).willReturn(verifyResponse("0.90"));
            given(similarityParser.parseDoubleSimilarity("0.90")).willReturn(0.90);
            given(similarityParser.isMatchingBySimilarity(0.90)).willReturn(true);

            VerifyByDescriptorResult result = verifyByDescriptorUseCase.execute(input);

            assertThat(result.result()).isTrue();
            assertThat(result.similarity()).isEqualTo("0.90");
            assertThat(result.threshold()).isEqualTo("0.85");
            verify(faceMatchRepository).save(any(FaceMatch.class));
        }

        @Test
        @DisplayName("유사도 < 임계치 → result=false 반환, history.fail(NOT_MATCH) 호출")
        void execute_whenSimilarityBelowThreshold_returnsFailResult() {
            given(matchFeign.verifyByDescriptor(any())).willReturn(verifyResponse("0.60"));
            given(similarityParser.parseDoubleSimilarity("0.60")).willReturn(0.60);
            given(similarityParser.isMatchingBySimilarity(0.60)).willReturn(false);

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            VerifyByDescriptorResult result = verifyByDescriptorUseCase.execute(input);

            assertThat(result.result()).isFalse();
            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("NOT_MATCH");
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("MatchFeign 실패 → InvalidFaceModuleException 변환 및 history.fail() 호출")
        void execute_whenMatchFeignFails_throwsInvalidFaceModuleException() {
            given(matchFeign.verifyByDescriptor(any()))
                    .willThrow(new CustomFeignException("E006", "SERVER_ERROR", "매처 오류"));

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> verifyByDescriptorUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("SERVER_ERROR");

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("SERVER_ERROR");
        }
    }
}
