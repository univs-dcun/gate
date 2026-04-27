package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class InvalidPasswordException extends CustomAuthException {

    public InvalidPasswordException() {
        super(ErrorType.FAILED_WRONG_PASSWORD);
    }
}
