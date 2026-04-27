package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class DuplicateEmailException extends CustomAuthException {

    public DuplicateEmailException() {
        super(ErrorType.ALREADY_USE_EMAIL);
    }
}
