package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class AdminAlreadyInitializedException extends CustomAuthException {

    public AdminAlreadyInitializedException() {
        super(ErrorType.ADMIN_ALREADY_INITIALIZED);
    }
}
