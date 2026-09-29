package ai.univs.gate.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 업무 예외의 응답 message 를 오류 이름이 아닌 메시지 키로 찾을 수 있다 (UG-111).
 *
 * <p>사설망을 허용한 온프레미스에서 PJ-111 이 "외부에서 접근할 수 있는 주소여야 한다" 로 나가면 반대
 * 안내가 된다. 코드·type 은 그대로 두고 message 만 바꾼다. 실제 메시지 파일로 확인한다.
 */
@DisplayName("UG-111: 업무 예외의 메시지 키")
class BusinessExceptionMessageKeyTest {

    @RestController
    static class Probe {
        @GetMapping("/default")
        void byDefault() {
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        }

        @GetMapping("/keyed")
        void keyed() {
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED, "WEBHOOK_URL_NOT_ALLOWED_PRIVATE_ALLOWED");
        }
    }

    private MockMvc mvc() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler(new MessageService(source)))
                .build();
    }

    @Test
    @DisplayName("키를 주지 않으면 지금처럼 오류 이름으로 찾는다")
    void 기본() throws Exception {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            mvc().perform(get("/default").locale(Locale.KOREAN))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.code").value("PJ-111"))
                    .andExpect(jsonPath("$.errors.message").value("웹훅 URL은 외부에서 접근할 수 있는 http(s) 주소여야 합니다."));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("키를 주면 코드·type 은 같고 message 만 그 키의 문구다")
    void 키_지정() throws Exception {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            mvc().perform(get("/keyed").locale(Locale.KOREAN))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.code").value("PJ-111"))
                    .andExpect(jsonPath("$.errors.type").value("WEBHOOK_URL_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.errors.message").value(org.hamcrest.Matchers.containsString("사내망 주소")));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
