package ai.univs.auth.application.result;

import java.time.LocalDateTime;

public record AccountResult(
        long accountId,
        String email,
        LocalDateTime lastLoginAt
) {
}
