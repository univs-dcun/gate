package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class BadCredentialsException extends CustomAuthException {

    public BadCredentialsException() {
        super(ErrorType.FAILED_WRONG_PASSWORD);
    }
}
