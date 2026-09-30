package ai.univs.face.shared.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 라이브니스 엔진이 준 실패 문자열의 문구 (UG-346). 운영과 같은 MessageSource(useCodeAsDefaultMessage=true)로 본다.
 * 번들에 없으면 키 이름이 그대로 errors.message 로 나가던 것을 대체 문구로 막는다.
 */
@DisplayName("face MessageService.getMessageOr")
class MessageServiceTest {

    private final MessageService messageService = new MessageService(new LocaleConfig().messageSource());

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ko", "en"})
    @DisplayName("모르는 문자열은 대체 문구, 아는 키는 그 문구")
    void 대체_문구(String lang) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(lang));
        String fallback = messageService.getMessageOr("LIVENESS_FAILED", "LIVENESS_FAILED");

        assertThat(fallback).isNotBlank().isNotEqualTo("LIVENESS_FAILED");
        assertThat(messageService.getMessageOr("unknown engine reason", "LIVENESS_FAILED")).isEqualTo(fallback);
        assertThat(messageService.getMessageOr("TOO_MANY_FACES", "LIVENESS_FAILED"))
                .isNotEqualTo("TOO_MANY_FACES").isNotEqualTo(fallback);
    }
}
