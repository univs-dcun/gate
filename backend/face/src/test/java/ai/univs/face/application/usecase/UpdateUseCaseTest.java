package ai.univs.face.application.usecase;

import ai.univs.face.application.input.UpdateInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.UpdateResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
import ai.univs.face.infrastructure.feign.match.dto.VerifyFeignResponseDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateUseCaseTest {

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private ExtractService extractService;
    @Mock private SimilarityParser similarityParser;
    @Mock private MultipartFile faceImage;

    @InjectMocks
    private UpdateUseCase updateUseCase;

    private static final String BRANCH  = "branch-A";
    private static final String FACE_ID = "face-001";
    private static final String TXN     = "txn-001";
    private static final String CLIENT  = "client-A";

    private UpdateInput input;

    @BeforeEach
    void setUp() {
        input = new UpdateInput(BRANCH, FACE_ID, faceImage, TXN, CLIENT, true, true);
        given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                .willReturn(new ExtractResult("descriptor-xyz"));
    }

    @Nested
    @DisplayName("정상 처리")
    class Success {

        @Test
        @DisplayName("verify 통과 후 update 성공 → UpdateResult 반환")
        void execute_whenVerifyAndUpdateSucceed_returnsUpdateResult() {
            given(matchFeign.verifyById(any()))
                    .willReturn(new FeignResponseApi<>(true, new VerifyFeignResponseDTO("0.92"), null));
            given(similarityParser.parseDoubleSimilarity("0.92")).willReturn(0.92);
            given(similarityParser.isMatchingBySimilarity(0.92)).willReturn(true);
            given(matchFeign.update(any()))
                    .willReturn(new FeignResponseApi<>(true, new MatchFeignResponseDTO(BRANCH, FACE_ID), null));

            UpdateResult result = updateUseCase.execute(input);

            assertThat(result.faceId()).isEqualTo(FACE_ID);
            assertThat(result.branchName()).isEqualTo(BRANCH);
            assertThat(result.transactionUuid()).isEqualTo(TXN);
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("verify 유사도가 임계치 미만이면 MISMATCH 예외 발생 및 history.fail() 호출")
        void execute_whenSimilarityBelowThreshold_throwsInvalidFaceImageException() {
            given(matchFeign.verifyById(any()))
                    .willReturn(new FeignResponseApi<>(true, new VerifyFeignResponseDTO("0.50"), null));
            given(similarityParser.parseDoubleSimilarity("0.50")).willReturn(0.50);
            given(similarityParser.isMatchingBySimilarity(0.50)).willReturn(false);

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> updateUseCase.execute(input))
                    .isInstanceOf(InvalidFaceImageException.class)
                    .extracting(e -> ((InvalidFaceImageException) e).getErrorType())
                    .isEqualTo(ErrorType.MISMATCH);

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo(ErrorType.MISMATCH.name());
        }

        @Test
        @DisplayName("MatchFeign(verifyById) 실패 → InvalidFaceModuleException으로 변환 및 history.fail() 호출")
        void execute_whenVerifyFeignFails_throwsInvalidFaceModuleException() {
            given(matchFeign.verifyById(any()))
                    .willThrow(new CustomFeignException("E002", "FACE_NOT_FOUND", "얼굴 없음"));

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> updateUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("FACE_NOT_FOUND");

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("FACE_NOT_FOUND");
        }

        @Test
        @DisplayName("ExtractService 실패 시 예외가 그대로 전파된다")
        void execute_whenExtractFails_exceptionPropagates() {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;

            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willThrow(new InvalidFaceModuleException(errorType.getCode(), errorType.name(), "얼굴 없음"));

            assertThatThrownBy(() -> updateUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class);
        }
    }
}
