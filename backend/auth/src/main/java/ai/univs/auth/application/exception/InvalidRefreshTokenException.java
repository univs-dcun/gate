package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class InvalidRefreshTokenException extends CustomAuthException {

    public InvalidRefreshTokenException() {
        super(ErrorType.INVALID_REFRESH_TOKEN);
    }
}
