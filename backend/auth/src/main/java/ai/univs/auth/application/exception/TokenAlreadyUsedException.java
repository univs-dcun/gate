package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class TokenAlreadyUsedException extends CustomAuthException {

    public TokenAlreadyUsedException() {
        super(ErrorType.ALREADY_USED_PASSWORD_RESET_TOKEN);
    }
}
