package ai.univs.gate.modules.api_key.application.result;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;

import java.time.LocalDateTime;

public record ApiKeyResult(
        Long apiKeyId,
        String apiKey,
        String maskedApiKey,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        Boolean isActive
) {

    public static ApiKeyResult from(ApiKey apiKey, boolean showFullKey) {
        return new ApiKeyResult(
                apiKey.getId(),
                showFullKey ? apiKey.getApiKey() : null,
                maskApiKey(apiKey.getApiKey()),
                apiKey.getIssuedAt(),
                apiKey.getExpiresAt(),
                apiKey.getIsActive()
        );
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 20) {
            return apiKey;
        }
        return apiKey.substring(0, 12) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
