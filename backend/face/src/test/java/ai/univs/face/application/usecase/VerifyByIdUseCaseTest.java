package ai.univs.face.application.usecase;

import ai.univs.face.application.input.VerifyByIdInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.VerifyByIdResult;
import ai.univs.face.application.service.ExtractService;
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
import ai.univs.face.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerifyByIdUseCaseTest {

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private FaceMatchRepository faceMatchRepository;
    @Mock private ExtractService extractService;
    @Mock private SimilarityParser similarityParser;
    @Mock private MultipartFile faceImage;

    @InjectMocks
    private VerifyByIdUseCase verifyByIdUseCase;

    private static final String BRANCH  = "branch-A";
    private static final String FACE_ID = "face-001";
    private static final String TXN     = "txn-001";
    private static final String CLIENT  = "client-A";

    private VerifyByIdInput input;

    @BeforeEach
    void setUp() {
        input = new VerifyByIdInput(BRANCH, FACE_ID, faceImage, TXN, CLIENT, true, true);
        given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                .willReturn(new ExtractResult("descriptor-xyz"));
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
        @DisplayName("유사도 >= 임계치 → result=true 반환 및 성공 이력 저장")
        void execute_whenSimilarityAboveThreshold_returnsSuccessResult() {
            given(matchFeign.verifyById(any())).willReturn(verifyResponse("0.92"));
            given(similarityParser.parseDoubleSimilarity("0.92")).willReturn(0.92);
            given(similarityParser.isMatchingBySimilarity(0.92)).willReturn(true);

            VerifyByIdResult result = verifyByIdUseCase.execute(input);

            assertThat(result.result()).isTrue();
            assertThat(result.similarity()).isEqualTo("0.92");
            assertThat(result.transactionUuid()).isEqualTo(TXN);
            verify(faceMatchRepository).save(any(FaceMatch.class));
        }

        @Test
        @DisplayName("유사도 < 임계치 → result=false 반환, history.fail(NOT_MATCH) 호출")
        void execute_whenSimilarityBelowThreshold_returnsFailResult() {
            given(matchFeign.verifyById(any())).willReturn(verifyResponse("0.50"));
            given(similarityParser.parseDoubleSimilarity("0.50")).willReturn(0.50);
            given(similarityParser.isMatchingBySimilarity(0.50)).willReturn(false);

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            VerifyByIdResult result = verifyByIdUseCase.execute(input);

            assertThat(result.result()).isFalse();
            assertThat(result.similarity()).isEqualTo("0.50");
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
            given(matchFeign.verifyById(any()))
                    .willThrow(new CustomFeignException("E004", "SERVER_ERROR", "매처 서버 오류"));

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> verifyByIdUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("SERVER_ERROR");

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("SERVER_ERROR");
        }

        @Test
        @DisplayName("ExtractService 실패 시 예외가 그대로 전파된다")
        void execute_whenExtractFails_exceptionPropagates() {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;

            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willThrow(new InvalidFaceModuleException(errorType.getCode(), errorType.name(), "얼굴 없음"));

            assertThatThrownBy(() -> verifyByIdUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class);
        }
    }
}
