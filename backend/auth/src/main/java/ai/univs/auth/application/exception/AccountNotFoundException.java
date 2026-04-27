package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class AccountNotFoundException extends CustomAuthException {

    public AccountNotFoundException() {
        super(ErrorType.FAILED_ACCOUNT_NOT_FOUND);
    }
}
