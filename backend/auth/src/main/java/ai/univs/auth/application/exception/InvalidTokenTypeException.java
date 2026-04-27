package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class InvalidTokenTypeException extends CustomAuthException {

    public InvalidTokenTypeException() {
        super(ErrorType.INVALID_TOKEN_TYPE);
    }
}
