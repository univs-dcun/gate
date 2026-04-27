package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class InvalidAccessTokenException extends CustomAuthException {

    public InvalidAccessTokenException() {
        super(ErrorType.INVALID_ACCESS_TOKEN);
    }
}
