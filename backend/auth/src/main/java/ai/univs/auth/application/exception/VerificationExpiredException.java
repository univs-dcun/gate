package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class VerificationExpiredException extends CustomAuthException {

    public VerificationExpiredException() {
        super(ErrorType.EXPIRED_VERIFICATION);
    }
}
