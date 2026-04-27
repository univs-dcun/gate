package ai.univs.face.api.v1;

import ai.univs.face.application.result.*;
import ai.univs.face.application.usecase.*;
import ai.univs.face.shared.locale.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @MockBean private UpdateUseCase updateUseCase;
    @MockBean private DeleteUseCase deleteUseCase;
    @MockBean private VerifyByIdUseCase verifyByIdUseCase;
    @MockBean private VerifyByDescriptorUseCase verifyByDescriptorUseCase;
    @MockBean private VerifyByImageUseCase verifyByImageUseCase;
    @MockBean private IdentifyUseCase identifyUseCase;
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

    // ─── POST /api/v1/face (등록) ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/face — 얼굴 등록")
    class Register {

        @BeforeEach
        void setUp() {
            given(registerUseCase.execute(any()))
                    .willReturn(new RegisterResult("branch-A", "face-001", "txn-001"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void register_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("face-001"))
                    .andExpect(jsonPath("$.data.branchName").value("branch-A"));
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void register_missingBranchName_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face")
                            .file(validJpgFile)
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceId 누락 → 400")
        void register_missingFaceId_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void register_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face")
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자가 .png → 400")
        void register_pngFile_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face")
                            .file(invalidPngFile)
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("branchName 256자 초과 → 400")
        void register_branchNameTooLong_returns400() throws Exception {
            String tooLong = "a".repeat(256);
            mockMvc.perform(multipart("/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", tooLong)
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceId 256자 초과 → 400")
        void register_faceIdTooLong_returns400() throws Exception {
            String tooLong = "f".repeat(256);
            mockMvc.perform(multipart("/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A")
                            .param("faceId", tooLong))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void register_wrongHttpMethod_returns405() throws Exception {
            mockMvc.perform(get("/api/v1/face"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ─── PUT /api/v1/face (수정) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/face — 얼굴 이미지 교체")
    class Update {

        @BeforeEach
        void setUp() {
            given(updateUseCase.execute(any()))
                    .willReturn(new UpdateResult("branch-A", "face-001", "txn-001"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void update_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("face-001"));
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void update_missingBranchName_returns400() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/face")
                            .file(validJpgFile)
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceId 누락 → 400")
        void update_missingFaceId_returns400() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void update_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/face")
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자가 .png → 400")
        void update_pngFile_returns400() throws Exception {
            MockMultipartFile pngFile = new MockMultipartFile(
                    "faceImage", "face.png", "image/png", "img".getBytes());
            mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/face")
                            .file(pngFile)
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST 메서드 사용 → 등록 엔드포인트로 라우팅되어 faceId 검증 실패 → 400")
        void update_postMethodOnPutEndpoint_returns400() throws Exception {
            // POST /api/v1/face 는 register 이며 faceId 가 @NotBlank → 파일만 보내면 400
            mockMvc.perform(multipart("/api/v1/face")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── POST /api/v1/face/delete (삭제) ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/face/delete — 얼굴 삭제")
    class Delete {

        @BeforeEach
        void setUp() {
            given(deleteUseCase.execute(any()))
                    .willReturn(new DeleteResult("branch-A", "face-001", "txn-001"));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void delete_validRequest_returns200() throws Exception {
            mockMvc.perform(post("/api/v1/face/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"branchName": "branch-A", "faceId": "face-001"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("face-001"));
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void delete_missingBranchName_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/face/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"faceId": "face-001"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceId 누락 → 400")
        void delete_missingFaceId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/face/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"branchName": "branch-A"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void delete_wrongHttpMethod_returns405() throws Exception {
            mockMvc.perform(get("/api/v1/face/delete"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("Content-Type application/xml → 405 (GlobalExceptionHandler가 HttpMediaTypeNotSupportedException을 405로 처리)")
        void delete_wrongContentType_returns405() throws Exception {
            mockMvc.perform(post("/api/v1/face/delete")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<delete><branchName>branch-A</branchName></delete>"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ─── POST /api/v1/face/verify/id (1:1 faceId 검증) ──────────────────────────

    @Nested
    @DisplayName("POST /api/v1/face/verify/id — 1:1 faceId 얼굴 검증")
    class VerifyById {

        @BeforeEach
        void setUp() {
            given(verifyByIdUseCase.execute(any()))
                    .willReturn(new VerifyByIdResult("txn-001", "face-001", "0.92", "0.85", true));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void verifyById_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/verify/id")
                            .file(validJpgFile)
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.result").value(true))
                    .andExpect(jsonPath("$.data.similarity").value("0.92"));
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void verifyById_missingBranchName_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/verify/id")
                            .file(validJpgFile)
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void verifyById_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/verify/id")
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자 .png → 400")
        void verifyById_pngFile_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/verify/id")
                            .file(invalidPngFile)
                            .param("branchName", "branch-A")
                            .param("faceId", "face-001"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── POST /api/v1/face/verify/image (1:1 image 검증) ────────────────────────

    @Nested
    @DisplayName("POST /api/v1/face/verify/image — 1:1 이미지 얼굴 검증")
    class VerifyByImage {

        @BeforeEach
        void setUp() {
            given(verifyByImageUseCase.execute(any()))
                    .willReturn(new VerifyByImageResult("txn-001", "0.90", "0.85", true));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void verifyByImage_validRequest_returns200() throws Exception {
            MockMultipartFile targetFile = new MockMultipartFile(
                    "targetFaceImage", "target.jpg", MediaType.IMAGE_JPEG_VALUE, "img".getBytes());

            mockMvc.perform(multipart("/api/v1/face/verify/image")
                            .file(validJpgFile)
                            .file(targetFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.result").value(true));
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void verifyByImage_missingFaceImage_returns400() throws Exception {
            MockMultipartFile targetFile = new MockMultipartFile(
                    "targetFaceImage", "target.jpg", MediaType.IMAGE_JPEG_VALUE, "img".getBytes());

            mockMvc.perform(multipart("/api/v1/face/verify/image")
                            .file(targetFile))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("targetFaceImage 누락 → 400")
        void verifyByImage_missingTargetFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/verify/image")
                            .file(validJpgFile))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자 .png → 400")
        void verifyByImage_pngFaceImage_returns400() throws Exception {
            MockMultipartFile targetFile = new MockMultipartFile(
                    "targetFaceImage", "target.jpg", MediaType.IMAGE_JPEG_VALUE, "img".getBytes());

            mockMvc.perform(multipart("/api/v1/face/verify/image")
                            .file(invalidPngFile)
                            .file(targetFile))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── POST /api/v1/face/verify/descriptor (1:1 descriptor 검증) ──────────────

    @Nested
    @DisplayName("POST /api/v1/face/verify/descriptor — 1:1 descriptor 얼굴 검증")
    class VerifyByDescriptor {

        @BeforeEach
        void setUp() {
            given(verifyByDescriptorUseCase.execute(any()))
                    .willReturn(new VerifyByDescriptorResult("txn-001", "0.91", "0.85", true));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void verifyByDescriptor_validRequest_returns200() throws Exception {
            mockMvc.perform(post("/api/v1/face/verify/descriptor")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"descriptor": "desc-A", "targetDescriptor": "desc-B"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.result").value(true));
        }

        @Test
        @DisplayName("GET 메서드 사용 → 405")
        void verifyByDescriptor_wrongHttpMethod_returns405() throws Exception {
            mockMvc.perform(get("/api/v1/face/verify/descriptor"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("Content-Type application/xml → 405 (GlobalExceptionHandler가 HttpMediaTypeNotSupportedException을 405로 처리)")
        void verifyByDescriptor_wrongContentType_returns405() throws Exception {
            mockMvc.perform(post("/api/v1/face/verify/descriptor")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<request><descriptor>desc-A</descriptor></request>"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ─── POST /api/v1/face/identify (1:N 검증) ───────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/face/identify — 1:N 얼굴 검증")
    class Identify {

        @BeforeEach
        void setUp() {
            given(identifyUseCase.execute(any()))
                    .willReturn(new IdentifyResult("txn-001", "face-001", "0.93", "0.85", true));
        }

        @Test
        @DisplayName("유효한 요청 → 200 OK")
        void identify_validRequest_returns200() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/identify")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.result").value(true));
        }

        @Test
        @DisplayName("branchName 누락 → 400")
        void identify_missingBranchName_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/identify")
                            .file(validJpgFile))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 누락 → 400")
        void identify_missingFaceImage_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/identify")
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("faceImage 확장자 .png → 400")
        void identify_pngFile_returns400() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/identify")
                            .file(invalidPngFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 엔드포인트 → 404")
        void identify_unknownEndpoint_returns404() throws Exception {
            mockMvc.perform(multipart("/api/v1/face/identify/unknown")
                            .file(validJpgFile)
                            .param("branchName", "branch-A"))
                    .andExpect(status().isNotFound());
        }
    }
}
