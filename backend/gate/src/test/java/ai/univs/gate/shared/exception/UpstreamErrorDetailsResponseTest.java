package ai.univs.gate.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.gate.support.message.MessageService;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 응답 errors.details 는 gate 가 정한 공개 값만 싣는다 (UG-378). 하위의 details(match faceId 등)는 내부 식별자라 새면 안 된다.
 */
@DisplayName("UG-378: 응답 errors.details")
class UpstreamErrorDetailsResponseTest {

    private static final String UPSTREAM_ONLY_VALUE = "match-internal-face-id";

    @RestController
    static class Probe {

        @GetMapping("/duplicate/with-feature")
        void duplicateWithFeature() {
            throw new CustomFeignException("MATCH-003", "ALREADY_REGISTERED_DESCRIPTOR", "이미 등록된 얼굴",
                    Map.of("faceId", "dup-feature-id"))
                    .withDetails(Map.of("featureId", "dup-feature-id"));
        }

        @GetMapping("/duplicate/upstream-only")
        void duplicateUpstreamOnly() {
            throw new CustomFeignException("MATCH-003", "ALREADY_REGISTERED_DESCRIPTOR", "이미 등록된 얼굴",
                    Map.of("faceId", UPSTREAM_ONLY_VALUE));
        }

        @GetMapping("/upstream/internal-error")
        void upstreamInternalError() {
            // 서버 오류 분기는 하위 code·message 를 감춘다 (UG-362) — details 도 마찬가지
            throw new CustomFeignException("SWAGGER-005", "INTERNAL_SERVER_ERROR", "match-server 호출 실패",
                    Map.of("faceId", UPSTREAM_ONLY_VALUE))
                    .withDetails(Map.of("featureId", UPSTREAM_ONLY_VALUE));
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler(new MessageService(source)))
                .build();
        LocaleContextHolder.setLocale(Locale.KOREAN);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("MATCH-003 에 gate 가 찾은 featureId 를 details 로 싣는다")
    void 공개_details() throws Exception {
        mvc.perform(get("/duplicate/with-feature").locale(Locale.KOREAN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("MATCH-003"))
                .andExpect(jsonPath("$.errors.type").value("ALREADY_REGISTERED_DESCRIPTOR"))
                .andExpect(jsonPath("$.errors.details.featureId").value("dup-feature-id"))
                .andExpect(jsonPath("$.errors.details.faceId").doesNotExist());
    }

    @Test
    @DisplayName("하위 details 만 있으면 응답에 details 가 없다 — match 의 faceId 를 그대로 내보내지 않는다")
    void 하위_details_는_새지_않는다() throws Exception {
        mvc.perform(get("/duplicate/upstream-only").locale(Locale.KOREAN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("MATCH-003"))
                .andExpect(jsonPath("$.errors.details").doesNotExist())
                .andExpect(content().string(not(containsString(UPSTREAM_ONLY_VALUE))));
    }

    @Test
    @DisplayName("하위 서버 오류로 감추는 응답에는 details 도 없다")
    void 서버_오류는_details_없음() throws Exception {
        mvc.perform(get("/upstream/internal-error").locale(Locale.KOREAN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("PJ-005"))
                .andExpect(jsonPath("$.errors.details").doesNotExist())
                .andExpect(content().string(not(containsString(UPSTREAM_ONLY_VALUE))));
    }
}
