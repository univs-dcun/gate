package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class VerificationNotFoundException extends CustomAuthException {

    public VerificationNotFoundException() {
        super(ErrorType.NOT_FOUND_VERIFICATION);
    }
}
