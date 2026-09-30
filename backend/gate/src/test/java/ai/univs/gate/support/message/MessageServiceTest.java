package ai.univs.gate.support.message;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.locale.LocaleConfig;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 실패 사유 문구 (UG-326, UG-346). 운영과 같은 MessageSource(LocaleConfig — useCodeAsDefaultMessage=true)로 확인한다.
 *
 * <p>UG-346 전에는 키가 없으면 코드가 그대로 나갔다. 실패 유형은 하위 서비스·라이브니스 엔진이 주는 값이라 가능한
 * 목록이 우리 코드에 없어, 빌드 시점 가드(MessageBundleCompletenessTest)로는 막을 수 없다 — 실행 중 대체 문구로 막는다.
 */
@DisplayName("MessageService: 실패 사유 문구")
class MessageServiceTest {

    private final MessageService messageService = new MessageService(new LocaleConfig().messageSource());

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ko", "en"})
    @DisplayName("등록된 유형은 그 문구, 소문자로 와도 같다, 빈 값은 빈 문자열")
    void 등록된_유형(String lang) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(lang));
        String notMatch = messageService.getFailureMessageOrEmpty("NOT_MATCH");

        assertThat(notMatch).isNotBlank().isNotEqualTo("NOT_MATCH");
        assertThat(messageService.getFailureMessageOrEmpty("not_match")).isEqualTo(notMatch);
        assertThat(messageService.getFailureMessageOrEmpty(null)).isEmpty();
        assertThat(messageService.getFailureMessageOrEmpty("  ")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ko", "en"})
    @DisplayName("번들에 없는 유형은 코드가 아니라 대체 문구 — 예외도 아니다(목록이 죽지 않는다)")
    void 미등록_유형(String lang) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(lang));

        String message = messageService.getFailureMessageOrEmpty("SOMETHING_THE_ENGINE_JUST_INVENTED");

        assertThat(message).isNotBlank()
                .isNotEqualToIgnoringCase("SOMETHING_THE_ENGINE_JUST_INVENTED")
                .isNotEqualTo(MessageService.UNKNOWN_FAILURE_REASON);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ko", "en"})
    @DisplayName("라이브니스는 모르는 엔진 문자열이면 「라이브니스 검증에 실패」 문구, 아는 사유는 그 문구")
    void 라이브니스(String lang) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(lang));
        String livenessFailed = messageService.getLivenessFailureMessage("LIVENESS_FAILED");

        assertThat(messageService.getLivenessFailureMessage("eyes blinking weirdly")).isEqualTo(livenessFailed);
        assertThat(livenessFailed).isNotBlank().isNotEqualTo(MessageService.LIVENESS_FAILED);
        assertThat(messageService.getLivenessFailureMessage("EYES_CLOSED"))
                .isNotEqualTo("EYES_CLOSED").isNotEqualTo(livenessFailed);
    }
}
