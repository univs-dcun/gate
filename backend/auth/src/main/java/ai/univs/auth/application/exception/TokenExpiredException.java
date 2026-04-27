package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class TokenExpiredException extends CustomAuthException {

    public TokenExpiredException() {
        super(ErrorType.EXPIRATION_TOKEN);
    }
}
