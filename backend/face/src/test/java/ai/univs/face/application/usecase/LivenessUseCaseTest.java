package ai.univs.face.application.usecase;

import ai.univs.face.application.input.LivenessInput;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
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
class LivenessUseCaseTest {

    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private ExtractService extractService;
    @Mock private MultipartFile faceImage;

    @InjectMocks
    private LivenessUseCase livenessUseCase;

    private static final String TXN    = "txn-001";
    private static final String CLIENT = "client-A";

    private LivenessInput input;

    @BeforeEach
    void setUp() {
        input = new LivenessInput(faceImage, TXN, CLIENT);
    }

    @Nested
    @DisplayName("정상 처리")
    class Success {

        @Test
        @DisplayName("extractForLiveness 성공 → LivenessResult 반환 및 이력 저장")
        void execute_success_returnsLivenessResult() {
            LivenessResult expected = new LivenessResult(true, "0.99", 0, "REAL", "high", "0.5");
            given(extractService.extractForLiveness(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willReturn(expected);

            LivenessResult result = livenessUseCase.execute(input);

            assertThat(result.success()).isTrue();
            assertThat(result.prdioction()).isEqualTo(0);
            assertThat(result.prdioctionDesc()).isEqualTo("REAL");

            // checkLiveness=true, checkMultiFace=true 고정값으로 항상 호출
            verify(extractService).extractForLiveness(any(), any(), any(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("extractForLiveness 성공 후 history.successLiveness() 호출")
        void execute_success_callsSuccessLiveness() {
            given(extractService.extractForLiveness(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willReturn(new LivenessResult(true, "0.99", 0, "REAL", "high", "0.5"));

            livenessUseCase.execute(input);

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);
            verify(faceHistoryRepository).save(captor.capture());
            // successLiveness 호출 결과로 result=true
            assertThat(captor.getValue().isResult()).isTrue();
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("extractForLiveness 실패 시 예외가 그대로 전파된다")
        void execute_whenExtractForLivenessFails_exceptionPropagates() {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;

            given(extractService.extractForLiveness(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willThrow(new InvalidFaceModuleException(errorType.getCode(), errorType.name(), "얼굴 없음"));

            assertThatThrownBy(() -> livenessUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("FACE_NOT_FOUND");
        }
    }
}
