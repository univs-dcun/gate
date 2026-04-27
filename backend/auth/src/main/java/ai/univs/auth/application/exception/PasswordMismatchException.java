package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class PasswordMismatchException extends CustomAuthException {

    public PasswordMismatchException() {
        super(ErrorType.FAILED_CONFIRM_PASSWORD);
    }
}
