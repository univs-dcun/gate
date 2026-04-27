package ai.univs.auth.api.dto;

import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record TokenValidationResponseDTO(
        @Schema(description = SwaggerDescriptions.VALID)
        boolean valid,
        @Schema(description = SwaggerDescriptions.ACCOUNT_ID)
        Long accountId
) {

    public static TokenValidationResponseDTO invalid() {
        return new TokenValidationResponseDTO(false, null);
    }

    public static TokenValidationResponseDTO valid(Long accountId) {
        return new TokenValidationResponseDTO(true, accountId);
    }
}
