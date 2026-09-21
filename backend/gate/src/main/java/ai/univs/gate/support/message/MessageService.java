package ai.univs.gate.support.message;

import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
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

    /**
     * 이력 목록의 실패 사유. 메시지 키가 없으면 <b>코드를 그대로</b> 돌려준다 (UG-326 반박 리뷰).
     *
     * <p>예전에는 {@link #getMessage(String)} 를 그대로 불러 {@code NoSuchMessageException} 이 그대로 올라갔다 —
     * 하위 서비스가 새 실패 타입을 하나만 내도 그 행이 든 목록 전체가 500 이었다. 통합 조회가 삭제 실패
     * 행(face·palm 의 다양한 타입)까지 노출하면서 그 면이 넓어졐다. 한 행의 번역 누락이 목록을 죽이면 안 된다.
     */
    public String getFailureMessageOrEmpty(String failureType) {
        if (!StringUtils.hasText(failureType)) {
            return "";
        }
        try {
            return getMessage(failureType);
        } catch (NoSuchMessageException e) {
            return failureType;
        }
    }
}
