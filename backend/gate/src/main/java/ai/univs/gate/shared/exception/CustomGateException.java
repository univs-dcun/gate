package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;

public class CustomGateException extends BusinessException {

    public CustomGateException(ErrorType errorType) {
        super(errorType);
    }
}
