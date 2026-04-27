package ai.univs.auth.shared.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class CustomAuthServiceException extends BusinessException {

    public CustomAuthServiceException(ErrorType errorType) {
        super(errorType);
    }
}
