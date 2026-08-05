package ai.univs.face.api.v2;

import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.IdentifyResult;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.application.result.RegisterResult;
import ai.univs.face.application.usecase.ExtractUseCase;
import ai.univs.face.application.usecase.IdentifyByDescriptorUseCase;
import ai.univs.face.application.usecase.LivenessUseCase;
import ai.univs.face.application.usecase.RegisterByDescriptorUseCase;
import ai.univs.face.application.usecase.RegisterUseCase;
import ai.univs.face.shared.locale.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = FaceController.class,
        properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false"
        }
)
class FaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RegisterUseCase registerUseCase;
    @MockBean private LivenessUseCase livenessUseCase;
    @MockBean private ExtractUseCase extractUseCase;
    @MockBean private RegisterByDescriptorUseCase registerByDescriptorUseCase;
    @MockBean private IdentifyByDescriptorUseCase identifyByDescriptorUseCase;
    @MockBean private MessageService messageService;

    private MockMultipartFile validJpgFile;
    private MockMultipartFile invalidPngFile;

    @BeforeEach
    void setUp() {
        validJpgFile = new MockMultipartFile(
                "faceImage", "face.jpg", MediaType.IMAGE_JPEG_VALUE, "image-content".getBytes());
        invalidPngFile = new MockMultipartFile(
                "faceImage", "face.png", "image/png", "image-content".getBytes());

        given(messageService.getMessage(any(String.class))).willReturn("에러 메시지");
    }

    // ─── POST /api/v2/face (등록) ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v2/face — 얼굴 등록 V2 (faceId 서버 생성)")
    class Register {

        @BeforeEach
        void setUp() {
            given(registerUseCase.execute(any()))
                    .willReturn(new RegisterResult("branch-A", "server-generated-id", "txn-001"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK, faceId는 서버가 생성")
        void register_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v2/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("server-generated-id"))
                    .andExpect(jsonPath("$.data.branchName").value("branch-A"))
                    .andExpect(jsonPath("$.data.transactionUuid").value("txn-001"));
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void register_missingBranchName_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face")
                            .file(validJpgFile))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void register_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face")
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자 .png → 400")
        void register_pngFile_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face")
                            .file(invalidPngFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("branchName 256자 초과 → 400")
        void register_branchNameTooLong_returns400() throws Exception {
            String tooLong = "a".repeat(256);
            mockMvc.perform(multipart("/api/v2/face")
                            .file(validJpgFile)
                            .param("branchName", tooLong))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("V1과 달리 faceId 파라미터 없이도 성공 (서버에서 생성)")
        void register_noFaceIdParam_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v2/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void register_wrongHttpMethod_returns405() throws Exception {
            mockMvc.perform(get("/api/v2/face"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ─── POST /api/v2/face/liveness (라이브니스) ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/v2/face/liveness — 라이브니스 검사")
    class Liveness {

        @BeforeEach
        void setUp() {
            given(livenessUseCase.execute(any()))
                    .willReturn(new LivenessResult(true, "0.99", 0, "REAL", "high", "0.5"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void liveness_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/liveness")
                            .file(validJpgFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.prdioction").value(0))
                    .andExpect(jsonPath("$.data.prdioctionDesc").value("REAL"));
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void liveness_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/liveness"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자 .png → 400")
        void liveness_pngFile_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/liveness")
                            .file(invalidPngFile))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("jpeg 확장자도 허용")
        void liveness_jpegExtension_returns200() throws Exception {
            MockMultipartFile jpegFile = new MockMultipartFile(
                    "faceImage", "face.jpeg", MediaType.IMAGE_JPEG_VALUE, "img".getBytes());

            mockMvc.perform(multipart("/api/v2/face/liveness")
                            .file(jpegFile))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void liveness_wrongHttpMethod_returns405() throws Exception {
            mockMvc.perform(get("/api/v2/face/liveness"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ─── POST /api/v2/face/extract (특징점 추출) ──────────────────────────────────

    @Nested
    @DisplayName("POST /api/v2/face/extract — 특징점 추출")
    class Extract {

        @BeforeEach
        void setUp() {
            given(extractUseCase.execute(any()))
                    .willReturn(new ExtractResult("descriptor-abc-xyz"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK, descriptor 반환")
        void extract_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/extract")
                            .file(validJpgFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.descriptor").value("descriptor-abc-xyz"));
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void extract_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/extract"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자 .png → 400")
        void extract_pngFile_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/extract")
                            .file(invalidPngFile))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void extract_wrongHttpMethod_returns405() throws Exception {
            mockMvc.perform(get("/api/v2/face/extract"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("존재하지 않는 엔드포인트 → 404")
        void extract_unknownEndpoint_returns404() throws Exception {
            mockMvc.perform(multipart("/api/v2/face/unknown")
                            .file(validJpgFile))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── UG-279: descriptor 기반 등록/1:N ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v2/face/descriptor — descriptor 기반 등록 (UG-279)")
    class RegisterByDescriptor {

        @BeforeEach
        void setUp() {
            given(registerByDescriptorUseCase.execute(any()))
                    .willReturn(new RegisterResult("branch-A", "server-generated-id", "txn-001"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void 정상() throws Exception {
            mockMvc.perform(post("/api/v2/face/descriptor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"branchName\":\"branch-A\",\"descriptor\":\"AAAAAAAAAAAA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("server-generated-id"))
                    .andExpect(jsonPath("$.data.branchName").value("branch-A"));
        }

        @Test
        @DisplayName("descriptor 누락 → 400")
        void descriptor_누락() throws Exception {
            mockMvc.perform(post("/api/v2/face/descriptor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"branchName\":\"branch-A\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void branchName_누락() throws Exception {
            mockMvc.perform(post("/api/v2/face/descriptor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"descriptor\":\"AAAAAAAAAAAA\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("multipart 로 보내면 거부된다 — 이미지 기반 등록 경로와 섞이지 않는다")
        void multipart_거부() throws Exception {
            // 정확한 코드(405/415)는 스프링의 핸들러 매칭 내부 사정이라 고정하지 않는다.
            // 확인하려는 계약은 "요청이 거부되고 UseCase 가 호출되지 않는다" 다.
            mockMvc.perform(multipart("/api/v2/face/descriptor")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().is4xxClientError());

            then(registerByDescriptorUseCase).should(never()).execute(any());
        }
    }

    @Nested
    @DisplayName("POST /api/v2/face/identify/descriptor — descriptor 기반 1:N (UG-279)")
    class IdentifyByDescriptor {

        @BeforeEach
        void setUp() {
            given(identifyByDescriptorUseCase.execute(any()))
                    .willReturn(new IdentifyResult("txn-002", "matched-face-id", "0.97000", "0.85", true));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void 정상() throws Exception {
            mockMvc.perform(post("/api/v2/face/identify/descriptor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"branchName\":\"branch-A\",\"descriptor\":\"AAAAAAAAAAAA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("matched-face-id"))
                    .andExpect(jsonPath("$.data.similarity").value("0.97000"))
                    .andExpect(jsonPath("$.data.result").value(true));
        }

        @Test
        @DisplayName("descriptor 누락 → 400")
        void descriptor_누락() throws Exception {
            mockMvc.perform(post("/api/v2/face/identify/descriptor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"branchName\":\"branch-A\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void 메서드_불일치() throws Exception {
            mockMvc.perform(get("/api/v2/face/identify/descriptor"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
