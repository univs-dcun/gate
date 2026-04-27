package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;

public class TokenExpiredException extends BusinessException {

    public TokenExpiredException(ErrorType errorType) {
        super(errorType);
    }
}
