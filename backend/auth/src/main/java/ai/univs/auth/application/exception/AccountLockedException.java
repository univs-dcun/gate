package ai.univs.auth.application.exception;

import ai.univs.auth.shared.web.enums.ErrorType;

public class AccountLockedException extends CustomAuthException {

    public AccountLockedException() {
        super(ErrorType.FAILED_ACCOUNT_LOCKED);
    }
}
