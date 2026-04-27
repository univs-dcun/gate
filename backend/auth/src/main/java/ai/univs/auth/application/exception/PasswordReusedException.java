package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class PasswordReusedException extends CustomAuthException {

    public PasswordReusedException() {
        super(ErrorType.ALREADY_USED_PASSWORD);
    }
}
