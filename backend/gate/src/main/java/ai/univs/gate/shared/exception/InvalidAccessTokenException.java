package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;

public class InvalidAccessTokenException extends BusinessException {

    public InvalidAccessTokenException(ErrorType errorType) {
        super(errorType);
    }
}
