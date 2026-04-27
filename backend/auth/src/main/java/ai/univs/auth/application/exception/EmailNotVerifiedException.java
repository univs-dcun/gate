package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class EmailNotVerifiedException extends CustomAuthException {

    public EmailNotVerifiedException() {
        super(ErrorType.NOT_EMAIL_VERIFIED);
    }
}
