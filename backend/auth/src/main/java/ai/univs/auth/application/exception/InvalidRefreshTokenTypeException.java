package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class InvalidRefreshTokenTypeException extends CustomAuthException {

    public InvalidRefreshTokenTypeException() {
        super(ErrorType.INVALID_REFRESH_TOKEN_TYPE);
    }
}
