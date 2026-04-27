package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class AccountInactiveException extends CustomAuthException {

    public AccountInactiveException() {
        super(ErrorType.FAILED_ACCOUNT_LOCKED);
    }
}
