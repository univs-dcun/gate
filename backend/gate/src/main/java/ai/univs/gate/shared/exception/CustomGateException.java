package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;

public class CustomGateException extends BusinessException {

    public CustomGateException(ErrorType errorType) {
        super(errorType);
    }

    /** 응답 message 만 다른 키로 찾는다. {@link BusinessException#getMessageKey()} 참고. */
    public CustomGateException(ErrorType errorType, String messageKey) {
        super(errorType, messageKey);
    }
}
