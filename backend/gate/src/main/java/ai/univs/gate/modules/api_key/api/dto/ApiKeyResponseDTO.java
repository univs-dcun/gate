package ai.univs.gate.modules.api_key.api.dto;

import ai.univs.gate.modules.api_key.application.result.ApiKeyResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record ApiKeyResponseDTO(
        @Schema(description = SwaggerDescriptions.API_KEY_ID)
        Long apiKeyId,
        @Schema(description = SwaggerDescriptions.API_KEY)
        String apiKey,
        @Schema(description = SwaggerDescriptions.MASKED_API_KEY)
        String maskedApiKey,
        @Schema(description = SwaggerDescriptions.ISSUED_AT)
        LocalDateTime issuedAt,
        @Schema(description = SwaggerDescriptions.EXPIRES_AT)
        LocalDateTime expiresAt,
        @Schema(description = SwaggerDescriptions.IS_ACTIVE)
        Boolean isActive
) {

    public static ApiKeyResponseDTO from(ApiKeyResult result, String timezone) {
        return new ApiKeyResponseDTO(
                result.apiKeyId(),
                result.apiKey(),
                result.maskedApiKey(),
                fromUtc(result.issuedAt(), timezone),
                fromUtc(result.expiresAt(), timezone),
                result.isActive());
    }
}
