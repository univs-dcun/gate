package ai.univs.face.application.service;

import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceLiveness;
import ai.univs.face.infrastructure.feign.extract.ExtractFeign;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractBodyFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseApi;
import ai.univs.face.infrastructure.feign.extract.dto.ExtractFeignResponseDTO;
import ai.univs.face.infrastructure.feign.extract.dto.LivenessBodyFeignResponseDTO;
import ai.univs.face.infrastructure.repository.FaceLivenessJpaRepository;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.locale.MessageService;
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

import static ai.univs.face.shared.web.enums.ErrorType.FACE_NOT_FOUND;
import static ai.univs.face.shared.web.enums.ErrorType.TOO_MANY_FACES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtractServiceTest {

    @Mock
    private ExtractFeign extractFeign;

    @Mock
    private FaceLivenessJpaRepository faceLivenessRepository;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ExtractService extractService;

    @Mock
    private MultipartFile faceImage;

    private FaceHistory faceHistory;
    private static final String CLIENT_ID = "test-client";

    @BeforeEach
    void setUp() {
        faceHistory = FaceHistory.create(
                ActionType.ADD,
                "face-001",
                "txn-uuid-001",
                CLIENT_ID,
                false,
                false
        );
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private ExtractFeignResponseApi<ExtractFeignResponseDTO> successResponse(String descriptor,
                                                                              int faceCount,
                                                                              LivenessBodyFeignResponseDTO liveness
    ) {
        ExtractBodyFeignResponseDTO body = new ExtractBodyFeignResponseDTO("face_img_base64", descriptor);
        ExtractFeignResponseDTO data = new ExtractFeignResponseDTO(body, liveness, faceCount);
        return new ExtractFeignResponseApi<>("SUCCESS", "ok", data);
    }

    private LivenessBodyFeignResponseDTO livenessPass() {
        return new LivenessBodyFeignResponseDTO("0.99", 0, "REAL", "high", "0.5");
    }

    private LivenessBodyFeignResponseDTO livenessFail(String desc) {
        return new LivenessBodyFeignResponseDTO("0.10", 1, desc, "low", "0.5");
    }

    // ─── extract() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extract()")
    class Extract {

        @Test
        @DisplayName("정상 추출 시 descriptor를 담은 ExtractResult를 반환한다")
        void extract_success_returnsDescriptor() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("descriptor-xyz", 1, null));

            ExtractResult result = extractService.extract(faceHistory, faceImage, CLIENT_ID, false, false);

            assertThat(result.descriptor()).isEqualTo("descriptor-xyz");
        }

        @Test
        @DisplayName("응답 코드가 SUCCESS가 아니면 FACE_NOT_FOUND로 실패 처리하고 예외를 던진다")
        void extract_failCode_throwsInvalidFaceModuleException() {
            ExtractBodyFeignResponseDTO body = new ExtractBodyFeignResponseDTO("img", "desc");
            ExtractFeignResponseDTO data = new ExtractFeignResponseDTO(body, null, 1);
            ExtractFeignResponseApi<ExtractFeignResponseDTO> failResponse =
                    new ExtractFeignResponseApi<>("FAIL", "error", data);

            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(failResponse);
            given(messageService.getMessage(FACE_NOT_FOUND.name())).willReturn("얼굴을 찾을 수 없습니다.");

            assertThatThrownBy(() ->
                    extractService.extract(faceHistory, faceImage, CLIENT_ID, false, false))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo(FACE_NOT_FOUND.name());

            assertThat(faceHistory.getFailureMessage()).isEqualTo(FACE_NOT_FOUND.name());
        }

        @Test
        @DisplayName("faceCount가 0이면 FACE_NOT_FOUND로 실패 처리하고 예외를 던진다")
        void extract_zeroFaceCount_throwsInvalidFaceModuleException() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 0, null));
            given(messageService.getMessage(FACE_NOT_FOUND.name())).willReturn("얼굴을 찾을 수 없습니다.");

            assertThatThrownBy(() ->
                    extractService.extract(faceHistory, faceImage, CLIENT_ID, false, false))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo(FACE_NOT_FOUND.name());
        }

        @Test
        @DisplayName("checkMultiFace=true이고 faceCount > 1이면 TOO_MANY_FACES 예외를 던진다")
        void extract_multiFace_throwsInvalidFaceModuleException() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 2, null));
            given(messageService.getMessage(TOO_MANY_FACES.name())).willReturn("얼굴이 여러 개 감지되었습니다.");

            assertThatThrownBy(() ->
                    extractService.extract(faceHistory, faceImage, CLIENT_ID, false, true))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo(TOO_MANY_FACES.name());
        }

        @Test
        @DisplayName("checkLiveness=true이고 liveness 통과 시 FaceLiveness를 저장하고 결과를 반환한다")
        void extract_livenessPass_savesFaceLivenessAndReturnsResult() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessPass()));

            ExtractResult result = extractService.extract(faceHistory, faceImage, CLIENT_ID, true, false);

            assertThat(result.descriptor()).isEqualTo("desc");
            verify(faceLivenessRepository).save(any(FaceLiveness.class));
        }

        @Test
        @DisplayName("checkLiveness=true이고 liveness 실패 시 FaceLiveness를 저장하고 예외를 던진다")
        void extract_livenessFail_savesFaceLivenessAndThrows() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessFail("FAKE")));
            given(messageService.getMessage("FAKE")).willReturn("위조된 얼굴입니다.");

            assertThatThrownBy(() ->
                    extractService.extract(faceHistory, faceImage, CLIENT_ID, true, false))
                    .isInstanceOf(InvalidFaceModuleException.class)
                    .extracting(e -> ((InvalidFaceModuleException) e).getType())
                    .isEqualTo("FAKE");

            verify(faceLivenessRepository).save(any(FaceLiveness.class));
            assertThat(faceHistory.getFailureMessage()).isEqualTo("FAKE");
        }

        @Test
        @DisplayName("FaceLiveness 저장 시 threshold가 null이면 빈 문자열로 저장된다")
        void extract_livenessNullThreshold_savesEmptyString() {
            LivenessBodyFeignResponseDTO livenessNullThreshold =
                    new LivenessBodyFeignResponseDTO("0.99", 0, "REAL", "high", null);
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessNullThreshold));

            extractService.extract(faceHistory, faceImage, CLIENT_ID, true, false);

            ArgumentCaptor<FaceLiveness> captor = ArgumentCaptor.forClass(FaceLiveness.class);
            verify(faceLivenessRepository).save(captor.capture());
            assertThat(captor.getValue().getThreshold()).isEqualTo("");
        }

        @Test
        @DisplayName("checkLiveness=false이면 liveness 검증을 수행하지 않는다")
        void extract_livenessDisabled_skipsLivenessValidation() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, null));

            extractService.extract(faceHistory, faceImage, CLIENT_ID, false, false);

            verifyNoInteractions(faceLivenessRepository);
        }
    }

    // ─── extractForLiveness() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("extractForLiveness()")
    class ExtractForLiveness {

        @Test
        @DisplayName("정상 liveness 통과 시 LivenessResult를 반환한다")
        void extractForLiveness_livenessPass_returnsLivenessResult() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessPass()));

            LivenessResult result = extractService.extractForLiveness(faceHistory, faceImage, CLIENT_ID, true, false);

            assertThat(result.success()).isTrue();
            assertThat(result.prdioction()).isEqualTo(0);
            assertThat(result.prdioctionDesc()).isEqualTo("REAL");
            verify(faceLivenessRepository).save(any(FaceLiveness.class));
        }

        @Test
        @DisplayName("응답 코드가 SUCCESS가 아니면 예외를 던진다")
        void extractForLiveness_failCode_throwsException() {
            ExtractFeignResponseApi<ExtractFeignResponseDTO> failResponse =
                    new ExtractFeignResponseApi<>("FAIL", "err",
                            new ExtractFeignResponseDTO(new ExtractBodyFeignResponseDTO(), null, 1));
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(failResponse);
            given(messageService.getMessage(FACE_NOT_FOUND.name())).willReturn("얼굴 없음");

            assertThatThrownBy(() ->
                    extractService.extractForLiveness(faceHistory, faceImage, CLIENT_ID, false, false))
                    .isInstanceOf(InvalidFaceModuleException.class);
        }

        @Test
        @DisplayName("checkMultiFace=true이고 faceCount > 1이면 faceHistory.fail()을 호출하고 예외를 던지지 않는다")
        void extractForLiveness_multiFace_callsFailWithoutThrowing() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 2, livenessPass()));

            LivenessResult result = extractService.extractForLiveness(faceHistory, faceImage, CLIENT_ID, true, true);

            assertThat(faceHistory.getFailureMessage()).isEqualTo(TOO_MANY_FACES.name());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("checkLiveness=true이고 prdioction != 0이면 faceHistory.fail()을 호출하고 예외를 던지지 않는다")
        void extractForLiveness_livenessFail_callsFailWithoutThrowing() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessFail("FAKE")));

            LivenessResult result = extractService.extractForLiveness(faceHistory, faceImage, CLIENT_ID, true, false);

            assertThat(faceHistory.getFailureMessage()).isEqualTo("FAKE");
            assertThat(result.success()).isFalse();
            assertThat(result.prdioction()).isEqualTo(1);
            verify(faceLivenessRepository).save(any(FaceLiveness.class));
        }

        @Test
        @DisplayName("checkLiveness=false이면 FaceLiveness를 저장하지 않는다")
        void extractForLiveness_livenessDisabled_doesNotSaveLiveness() {
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessPass()));

            extractService.extractForLiveness(faceHistory, faceImage, CLIENT_ID, false, false);

            verifyNoInteractions(faceLivenessRepository);
        }

        @Test
        @DisplayName("liveness threshold가 null이면 빈 문자열로 저장된다")
        void extractForLiveness_nullThreshold_savesEmptyString() {
            LivenessBodyFeignResponseDTO livenessNullThreshold =
                    new LivenessBodyFeignResponseDTO("0.99", 0, "REAL", "high", null);
            given(extractFeign.extractWithOptionalLivenessAndMultiFace(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                    .willReturn(successResponse("desc", 1, livenessNullThreshold));

            extractService.extractForLiveness(faceHistory, faceImage, CLIENT_ID, true, false);

            ArgumentCaptor<FaceLiveness> captor = ArgumentCaptor.forClass(FaceLiveness.class);
            verify(faceLivenessRepository).save(captor.capture());
            assertThat(captor.getValue().getThreshold()).isEqualTo("");
        }
    }
}
