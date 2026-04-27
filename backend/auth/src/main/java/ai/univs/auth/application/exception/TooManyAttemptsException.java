package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class TooManyAttemptsException extends CustomAuthException {

    public TooManyAttemptsException() {
        super(ErrorType.TOO_MANY_ATTEMPTS_VERIFICATION);
    }
}
