package ai.univs.auth.application.result;

import ai.univs.auth.domain.entity.Account;

import java.time.LocalDateTime;

public record SignupResult(
        Long accountId,
        String email,
        LocalDateTime createdAt
) {

    public static SignupResult of(Account account) {
        return new SignupResult(account.getAccountId(), account.getEmail(), account.getCreatedAt());
    }
}
