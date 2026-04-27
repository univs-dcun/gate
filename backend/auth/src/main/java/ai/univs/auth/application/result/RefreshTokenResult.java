package ai.univs.auth.application.result;

import java.time.LocalDateTime;

public record RefreshTokenResult(
        String token,
        String jti,
        LocalDateTime expiresAt
) {
}
