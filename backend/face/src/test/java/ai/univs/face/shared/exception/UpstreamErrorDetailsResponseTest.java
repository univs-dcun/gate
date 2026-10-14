package ai.univs.face.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.face.shared.locale.MessageService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하위 서비스 오류의 errors.details 를 그대로 넘긴다 (UG-378). gate 가 MATCH-003 의 faceId 로 겹친 특징점을 찾는다.
 */
@DisplayName("UG-378: 하위 오류 details 전달")
class UpstreamErrorDetailsResponseTest {

    @RestController
    static class Probe {

        @GetMapping("/module/duplicate")
        void duplicate() {
            throw new InvalidFaceModuleException("MATCH-003", "ALREADY_REGISTERED_DESCRIPTOR", "이미 등록된 얼굴",
                    Map.of("faceId", "dup-face"));
        }

        @GetMapping("/module/plain")
        void plain() {
            throw new InvalidFaceModuleException("MATCH-004", "INVALID_FACE_ID", "없는 faceId");
        }

        @GetMapping("/feign/duplicate")
        void feignDuplicate() {
            throw new CustomFeignException("MATCH-003", "ALREADY_REGISTERED_DESCRIPTOR", "이미 등록된 얼굴",
                    Map.of("faceId", "dup-face"));
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler(Mockito.mock(MessageService.class)))
                .build();
    }

    @Test
    @DisplayName("InvalidFaceModuleException 의 details 가 응답 errors.details 로 나간다")
    void 모듈_오류_details() throws Exception {
        mvc.perform(get("/module/duplicate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("MATCH-003"))
                .andExpect(jsonPath("$.errors.details.faceId").value("dup-face"));
    }

    @Test
    @DisplayName("CustomFeignException 도 같다")
    void 피인_오류_details() throws Exception {
        mvc.perform(get("/feign/duplicate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.details.faceId").value("dup-face"));
    }

    @Test
    @DisplayName("details 가 없으면 응답에 details 키가 없다 — 예전 응답 모양 그대로")
    void details_없으면_키_없음() throws Exception {
        mvc.perform(get("/module/plain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("MATCH-004"))
                .andExpect(jsonPath("$.errors.details").doesNotExist())
                // doesNotExist 는 null 값도 통과시킨다 — 키 자체가 없음을 본문으로 확인한다
                .andExpect(content().string(not(containsString("\"details\""))));
    }
}
