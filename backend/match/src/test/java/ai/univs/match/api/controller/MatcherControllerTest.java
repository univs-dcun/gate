package ai.univs.match.api.controller;

import ai.univs.match.api.dto.*;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.result.VerifyResult;
import ai.univs.match.application.usecase.*;
import ai.univs.match.shared.locale.MessageService;
import ai.univs.match.shared.web.enums.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatcherController.class)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
@DisplayName("MatcherController")
class MatcherControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RegisterWithFaceIdUseCase registerWithFaceIdUseCase;
    @MockBean private RegisterUseCase registerUseCase;
    @MockBean private UpdateUseCase updateUseCase;
    @MockBean private DeleteUseCase deleteUseCase;
    @MockBean private VerifyByFaceIdUseCase verifyByFaceIdUseCase;
    @MockBean private VerifyByDescriptorUseCase verifyByDescriptorUseCase;
    @MockBean private IdentifyUseCase identifyUseCase;
    @MockBean private MessageService messageService;

    private static final String BRANCH_NAME = "testBranch";
    private static final String FACE_ID = "face-001";
    private static final String DESCRIPTOR = "descriptor-value";
    private static final String LONG_STRING = "a".repeat(256); // 길이 제한 256자 초과

    @BeforeEach
    void setUp() {
        when(messageService.getMessage(anyString())).thenReturn("validation error");
        when(messageService.getMessage(any(ErrorType.class))).thenReturn("error");
    }

    // =========================================================================
    // POST /api/v1/match/face-id
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/match/face-id - faceId 기반 특징점 등록")
    class RegisterWithFaceId {

        private static final String URL = "/api/v1/match/face-id";

        @Test
        @DisplayName("유효한 요청이면 200과 branchName/faceId를 반환한다")
        void whenValidRequest_thenReturns200WithResponse() throws Exception {
            when(registerWithFaceIdUseCase.execute(any())).thenReturn(new MatchResult(BRANCH_NAME, FACE_ID));
            var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, FACE_ID, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.branchName").value(BRANCH_NAME))
                    .andExpect(jsonPath("$.data.faceId").value(FACE_ID))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("유효한 요청이면 use case를 호출한다")
        void whenValidRequest_thenDelegatestoUseCase() throws Exception {
            var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, FACE_ID, DESCRIPTOR);

            perform(post(URL), request);

            verify(registerWithFaceIdUseCase).execute(any());
        }

        @Nested
        @DisplayName("branchName 검증")
        class BranchNameValidation {

            @Test
            @DisplayName("branchName이 null이면 400을 반환한다")
            void whenBranchNameIsNull_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(null, FACE_ID, DESCRIPTOR);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }

            @Test
            @DisplayName("branchName이 공백 문자열이면 400을 반환한다")
            void whenBranchNameIsBlank_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO("   ", FACE_ID, DESCRIPTOR);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }

            @Test
            @DisplayName("branchName이 255자 초과이면 400을 반환한다")
            void whenBranchNameExceeds255Chars_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(LONG_STRING, FACE_ID, DESCRIPTOR);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }
        }

        @Nested
        @DisplayName("faceId 검증")
        class FaceIdValidation {

            @Test
            @DisplayName("faceId가 null이면 400을 반환한다")
            void whenFaceIdIsNull_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, null, DESCRIPTOR);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }

            @Test
            @DisplayName("faceId가 공백 문자열이면 400을 반환한다")
            void whenFaceIdIsBlank_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, "  ", DESCRIPTOR);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }

            @Test
            @DisplayName("faceId가 255자 초과이면 400을 반환한다")
            void whenFaceIdExceeds255Chars_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, LONG_STRING, DESCRIPTOR);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }
        }

        @Nested
        @DisplayName("descriptor 검증")
        class DescriptorValidation {

            @Test
            @DisplayName("descriptor가 null이면 400을 반환한다")
            void whenDescriptorIsNull_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, FACE_ID, null);

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }

            @Test
            @DisplayName("descriptor가 공백 문자열이면 400을 반환한다")
            void whenDescriptorIsBlank_thenReturns400() throws Exception {
                var request = new RegisterWithFaceIdRequestDTO(BRANCH_NAME, FACE_ID, "  ");

                perform(post(URL), request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
            }
        }
    }

    // =========================================================================
    // POST /api/v1/match
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/match - 매처 생성 faceId로 특징점 등록")
    class Register {

        private static final String URL = "/api/v1/match";

        @Test
        @DisplayName("유효한 요청이면 200과 branchName, use case가 생성한 faceId를 반환한다")
        void whenValidRequest_thenReturns200WithGeneratedFaceId() throws Exception {
            when(registerUseCase.execute(anyString(), anyString())).thenReturn(new MatchResult(BRANCH_NAME, "generated-uuid"));
            var request = new RegisterRequestDTO(BRANCH_NAME, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.branchName").value(BRANCH_NAME))
                    .andExpect(jsonPath("$.data.faceId").value("generated-uuid"));
        }

        @Test
        @DisplayName("branchName이 null이면 400을 반환한다")
        void whenBranchNameIsNull_thenReturns400() throws Exception {
            var request = new RegisterRequestDTO(null, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("branchName이 255자 초과이면 400을 반환한다")
        void whenBranchNameExceedsLimit_thenReturns400() throws Exception {
            var request = new RegisterRequestDTO(LONG_STRING, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("descriptor가 null이면 400을 반환한다")
        void whenDescriptorIsNull_thenReturns400() throws Exception {
            var request = new RegisterRequestDTO(BRANCH_NAME, null);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // PUT /api/v1/match
    // =========================================================================

    @Nested
    @DisplayName("PUT /api/v1/match - 특징점 수정")
    class Update {

        private static final String URL = "/api/v1/match";

        @Test
        @DisplayName("유효한 요청이면 200을 반환한다")
        void whenValidRequest_thenReturns200() throws Exception {
            when(updateUseCase.execute(any())).thenReturn(new MatchResult(BRANCH_NAME, FACE_ID));
            var request = new UpdateRequestDTO(BRANCH_NAME, FACE_ID, DESCRIPTOR);

            perform(put(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.branchName").value(BRANCH_NAME));
        }

        @Test
        @DisplayName("branchName이 null이면 400을 반환한다")
        void whenBranchNameIsNull_thenReturns400() throws Exception {
            var request = new UpdateRequestDTO(null, FACE_ID, DESCRIPTOR);

            perform(put(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("faceId가 null이면 400을 반환한다")
        void whenFaceIdIsNull_thenReturns400() throws Exception {
            var request = new UpdateRequestDTO(BRANCH_NAME, null, DESCRIPTOR);

            perform(put(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("faceId가 255자 초과이면 400을 반환한다")
        void whenFaceIdExceedsLimit_thenReturns400() throws Exception {
            var request = new UpdateRequestDTO(BRANCH_NAME, LONG_STRING, DESCRIPTOR);

            perform(put(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("descriptor가 null이면 400을 반환한다")
        void whenDescriptorIsNull_thenReturns400() throws Exception {
            var request = new UpdateRequestDTO(BRANCH_NAME, FACE_ID, null);

            perform(put(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // POST /api/v1/match/delete
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/match/delete - 특징점 삭제")
    class Delete {

        private static final String URL = "/api/v1/match/delete";

        @Test
        @DisplayName("유효한 요청이면 200과 branchName/faceId를 반환한다")
        void whenValidRequest_thenReturns200() throws Exception {
            when(deleteUseCase.execute(anyString(), anyString())).thenReturn(new MatchResult(BRANCH_NAME, FACE_ID));
            var request = new DeleteRequestDTO(BRANCH_NAME, FACE_ID);

            perform(post(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.branchName").value(BRANCH_NAME))
                    .andExpect(jsonPath("$.data.faceId").value(FACE_ID));
        }

        @Test
        @DisplayName("branchName이 null이면 400을 반환한다")
        void whenBranchNameIsNull_thenReturns400() throws Exception {
            var request = new DeleteRequestDTO(null, FACE_ID);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("branchName이 255자 초과이면 400을 반환한다")
        void whenBranchNameExceedsLimit_thenReturns400() throws Exception {
            var request = new DeleteRequestDTO(LONG_STRING, FACE_ID);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("faceId가 null이면 400을 반환한다")
        void whenFaceIdIsNull_thenReturns400() throws Exception {
            var request = new DeleteRequestDTO(BRANCH_NAME, null);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("faceId가 255자 초과이면 400을 반환한다")
        void whenFaceIdExceedsLimit_thenReturns400() throws Exception {
            var request = new DeleteRequestDTO(BRANCH_NAME, LONG_STRING);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // POST /api/v1/match/verify/id
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/match/verify/id - 1:1 매칭 (faceId 기반)")
    class VerifyById {

        private static final String URL = "/api/v1/match/verify/id";

        @Test
        @DisplayName("유효한 요청이면 200과 similarity를 반환한다")
        void whenValidRequest_thenReturns200WithSimilarity() throws Exception {
            when(verifyByFaceIdUseCase.execute(any())).thenReturn(new VerifyResult("0.95000"));
            var request = new VerifyIdRequestDTO(BRANCH_NAME, FACE_ID, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.similarity").value("0.95000"));
        }

        @Test
        @DisplayName("branchName이 null이면 400을 반환한다")
        void whenBranchNameIsNull_thenReturns400() throws Exception {
            var request = new VerifyIdRequestDTO(null, FACE_ID, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("faceId가 null이면 400을 반환한다")
        void whenFaceIdIsNull_thenReturns400() throws Exception {
            var request = new VerifyIdRequestDTO(BRANCH_NAME, null, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("faceId가 255자 초과이면 400을 반환한다")
        void whenFaceIdExceedsLimit_thenReturns400() throws Exception {
            var request = new VerifyIdRequestDTO(BRANCH_NAME, LONG_STRING, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("descriptor가 null이면 400을 반환한다")
        void whenDescriptorIsNull_thenReturns400() throws Exception {
            var request = new VerifyIdRequestDTO(BRANCH_NAME, FACE_ID, null);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // POST /api/v1/match/verify/descriptor
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/match/verify/descriptor - 1:1 매칭 (특징점 간)")
    class VerifyByDescriptor {

        private static final String URL = "/api/v1/match/verify/descriptor";

        @Test
        @DisplayName("유효한 요청이면 200과 similarity를 반환한다")
        void whenValidRequest_thenReturns200WithSimilarity() throws Exception {
            when(verifyByDescriptorUseCase.execute(anyString(), anyString()))
                    .thenReturn(new VerifyResult("0.90000"));
            var request = new VerifyDescriptorRequestDTO(DESCRIPTOR, "target-descriptor");

            perform(post(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.similarity").value("0.90000"));
        }

        @Test
        @DisplayName("descriptor가 null이면 400을 반환한다")
        void whenDescriptorIsNull_thenReturns400() throws Exception {
            var request = new VerifyDescriptorRequestDTO(null, "target-descriptor");

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("descriptor가 공백 문자열이면 400을 반환한다")
        void whenDescriptorIsBlank_thenReturns400() throws Exception {
            var request = new VerifyDescriptorRequestDTO("  ", "target-descriptor");

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("targetDescriptor가 null이면 400을 반환한다")
        void whenTargetDescriptorIsNull_thenReturns400() throws Exception {
            var request = new VerifyDescriptorRequestDTO(DESCRIPTOR, null);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("targetDescriptor가 공백 문자열이면 400을 반환한다")
        void whenTargetDescriptorIsBlank_thenReturns400() throws Exception {
            var request = new VerifyDescriptorRequestDTO(DESCRIPTOR, "  ");

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // POST /api/v1/match/identify
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/match/identify - 1:N 매칭")
    class Identify {

        private static final String URL = "/api/v1/match/identify";

        @Test
        @DisplayName("유효한 요청이면 200과 faceId/similarity를 반환한다")
        void whenValidRequest_thenReturns200WithFaceIdAndSimilarity() throws Exception {
            when(identifyUseCase.execute(anyString(), anyString()))
                    .thenReturn(new IdentifyResult("matched-face", "0.92000"));
            var request = new IdentifyRequestDTO(BRANCH_NAME, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.faceId").value("matched-face"))
                    .andExpect(jsonPath("$.data.similarity").value("0.92000"));
        }

        @Test
        @DisplayName("branchName이 null이면 400을 반환한다")
        void whenBranchNameIsNull_thenReturns400() throws Exception {
            var request = new IdentifyRequestDTO(null, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("branchName이 255자 초과이면 400을 반환한다")
        void whenBranchNameExceedsLimit_thenReturns400() throws Exception {
            var request = new IdentifyRequestDTO(LONG_STRING, DESCRIPTOR);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("descriptor가 null이면 400을 반환한다")
        void whenDescriptorIsNull_thenReturns400() throws Exception {
            var request = new IdentifyRequestDTO(BRANCH_NAME, null);

            perform(post(URL), request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // 공통 응답 형식 검증
    // =========================================================================

    @Nested
    @DisplayName("공통 응답 형식")
    class CommonResponseFormat {

        @Test
        @DisplayName("성공 응답은 success=true, errors=null 구조를 가진다")
        void whenSuccess_thenResponseHasCorrectStructure() throws Exception {
            when(registerUseCase.execute(anyString(), anyString())).thenReturn(new MatchResult(BRANCH_NAME, FACE_ID));
            var request = new RegisterRequestDTO(BRANCH_NAME, DESCRIPTOR);

            perform(post("/api/v1/match"), request)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("실패 응답은 success=false, data=null, errors 존재 구조를 가진다")
        void whenValidationFails_thenResponseHasCorrectStructure() throws Exception {
            var request = new RegisterRequestDTO(null, DESCRIPTOR);

            perform(post("/api/v1/match"), request)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.errors").exists())
                    .andExpect(jsonPath("$.errors.code").value("COMMON-001"))
                    .andExpect(jsonPath("$.errors.type").value("INVALID_INPUT"));
        }
    }

    // =========================================================================
    // 공통 헬퍼
    // =========================================================================

    private ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Object requestBody
    ) throws Exception {
        return mockMvc.perform(builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)));
    }
}
