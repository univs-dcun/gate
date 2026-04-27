package ai.univs.face.application.usecase;

import ai.univs.face.application.input.RegisterInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.MatchFeignResponseDTO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock private MatchFeign matchFeign;
    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private ExtractService extractService;
    @Mock private MultipartFile faceImage;

    @InjectMocks
    private RegisterUseCase registerUseCase;

    private static final String BRANCH  = "branch-A";
    private static final String FACE_ID = "face-001";
    private static final String TXN     = "txn-001";
    private static final String CLIENT  = "client-A";

    @BeforeEach
    void setUp() {
        given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                .willReturn(new ExtractResult("descriptor-xyz"));
    }

    private RegisterInput inputWithFaceId(String faceId) {
        return new RegisterInput(faceId, faceImage, BRANCH, TXN, CLIENT, true, true);
    }

    private FeignResponseApi<MatchFeignResponseDTO> matchResponse(String branch, String faceId) {
        return new FeignResponseApi<>(true, new MatchFeignResponseDTO(branch, faceId), null);
    }

    @Nested
    @DisplayName("faceId 존재 여부에 따른 매처 API 분기")
    class MatcherApiRouting {

        @Test
        @DisplayName("faceId가 있으면 registerWithFaceId 호출 후 RegisterResult 반환")
        void execute_withFaceId_callsRegisterWithFaceId() {
            given(matchFeign.registerWithFaceId(any())).willReturn(matchResponse(BRANCH, FACE_ID));

            RegisterResult result = registerUseCase.execute(inputWithFaceId(FACE_ID));

            assertThat(result.faceId()).isEqualTo(FACE_ID);
            assertThat(result.branchName()).isEqualTo(BRANCH);
            assertThat(result.transactionUuid()).isEqualTo(TXN);
            verify(matchFeign).registerWithFaceId(any());
            verifyNoMoreInteractions(matchFeign);
        }

        @Test
        @DisplayName("faceId가 빈 문자열이면 register(V2) 호출 후 RegisterResult 반환")
        void execute_withoutFaceId_callsRegisterV2() {
            String serverGeneratedId = "server-generated-id";
            given(matchFeign.register(any())).willReturn(matchResponse(BRANCH, serverGeneratedId));

            RegisterResult result = registerUseCase.execute(inputWithFaceId(""));

            assertThat(result.faceId()).isEqualTo(serverGeneratedId);
            verify(matchFeign).register(any());
            verifyNoMoreInteractions(matchFeign);
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("MatchFeign 실패 → CustomFeignException을 InvalidFaceModuleException으로 변환하고 history.fail() 호출")
        void execute_whenMatchFeignFails_throwsInvalidFaceModuleException() {
            given(matchFeign.registerWithFaceId(any()))
                    .willThrow(new CustomFeignException("E001", "DUPLICATE", "이미 등록된 얼굴"));

            ArgumentCaptor<FaceHistory> captor = ArgumentCaptor.forClass(FaceHistory.class);

            assertThatThrownBy(() -> registerUseCase.execute(inputWithFaceId(FACE_ID)))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("DUPLICATE");

            verify(faceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getFailureMessage()).isEqualTo("DUPLICATE");
        }

        @Test
        @DisplayName("ExtractService 실패 시 예외가 그대로 전파된다")
        void execute_whenExtractFails_exceptionPropagates() {
            ErrorType errorType = ErrorType.FACE_NOT_FOUND;

            given(extractService.extract(any(), any(), any(), anyBoolean(), anyBoolean()))
                    .willThrow(new InvalidFaceModuleException(errorType.getCode(), errorType.name(), "얼굴 없음"));

            assertThatThrownBy(() -> registerUseCase.execute(inputWithFaceId(FACE_ID)))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("FACE_NOT_FOUND");
        }
    }
}
