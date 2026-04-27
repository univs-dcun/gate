package ai.univs.auth.application.result;

import java.time.LocalDateTime;

public record SendEmailVerificationCodeResult(
        String email,
        LocalDateTime expiresAt
) {
}
