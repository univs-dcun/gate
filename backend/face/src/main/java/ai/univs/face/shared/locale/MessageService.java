package ai.univs.face.shared.locale;

import java.util.Locale;
import ai.univs.face.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;

    public String getMessage(ErrorType type) {
        return messageSource.getMessage(type.name(), null, LocaleContextHolder.getLocale());
    }

    public String getMessage(String typeString) {
        return messageSource.getMessage(typeString.toUpperCase(), null, LocaleContextHolder.getLocale());
    }

    /**
     * 키가 없으면 {@code fallbackKey} 의 문구 (UG-346). 라이브니스 엔진이 주는 실패 문자열처럼 가능한 값이 우리 코드에
     * 없는 키에 쓴다 — useCodeAsDefaultMessage(true) 라 그냥 찾으면 키 이름이 문구로 나간다.
     */
    public String getMessageOr(String typeString, String fallbackKey) {
        Locale locale = LocaleContextHolder.getLocale();
        String missing = "\u0000missing-message";
        String message = messageSource.getMessage(typeString.toUpperCase(Locale.ROOT), null, missing, locale);
        return missing.equals(message) ? messageSource.getMessage(fallbackKey, null, fallbackKey, locale) : message;
    }
}
