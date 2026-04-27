package ai.univs.gate.support.message;

import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public String getFailureMessageOrEmpty(String failureType) {
        return StringUtils.hasText(failureType)
                ? getMessage(failureType)
                : "";
    }
}
