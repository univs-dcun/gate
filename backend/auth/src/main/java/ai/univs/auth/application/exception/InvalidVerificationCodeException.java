package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class InvalidVerificationCodeException extends CustomAuthException {

    public InvalidVerificationCodeException() {
        super(ErrorType.INVALID_VERIFICATION_CODE);
    }
}
