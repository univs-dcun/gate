package ai.univs.face.application.usecase;

import ai.univs.face.application.input.ExtractInput;
import ai.univs.face.application.result.ExtractResult;
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
class ExtractUseCaseTest {

    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private ExtractService extractService;
    @Mock private MultipartFile faceImage;

    @InjectMocks
    private ExtractUseCase extractUseCase;

    private static final String TXN    = "txn-001";
    private static final String CLIENT = "client-A";

    private ExtractInput input;

    @BeforeEach
    void setUp() {
        input = new ExtractInput(faceImage, TXN, CLIENT);
    }

    @Nested
    @DisplayName("정상 처리")
    class Success {

        @Test
        @DisplayName("extract 성공 → descriptor 반환")
        void execute_success_returnsDescriptor() {
            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willReturn(new ExtractResult("descriptor-abc"));

            ExtractResult result = extractUseCase.execute(input);

            assertThat(result.descriptor()).isEqualTo("descriptor-abc");
        }

        @Test
        @DisplayName("extract 성공 후 history.successExtract() 호출 (result=true)")
        void execute_success_callsSuccessExtract() {
            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willReturn(new ExtractResult("descriptor-abc"));

            extractUseCase.execute(input);

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);
            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().isResult()).isTrue();
        }

        @Test
        @DisplayName("extract 호출 시 liveness=false, multiFace=false 고정값으로 전달")
        void execute_always_callsExtractWithLivenessDisabled() {
            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willReturn(new ExtractResult("descriptor-abc"));

            extractUseCase.execute(input);

            // false, false 로 호출됐는지 검증
            verify(extractService).extract(any(), any(), any(),
                    org.mockito.ArgumentMatchers.eq(false),
                    org.mockito.ArgumentMatchers.eq(false));
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("extract 실패 시 예외가 그대로 전파된다")
        void execute_whenExtractFails_exceptionPropagates() {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;

            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willThrow(new InvalidFaceModuleException(errorType.getCode(), errorType.name(), "얼굴 없음"));

            assertThatThrownBy(() -> extractUseCase.execute(input))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("FACE_NOT_FOUND");
        }
    }
}
