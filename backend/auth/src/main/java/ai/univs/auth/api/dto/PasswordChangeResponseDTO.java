package ai.univs.auth.api.dto;

import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordChangeResponseDTO(
        @Schema(description = SwaggerDescriptions.ACCOUNT_ID)
        Long accountId
) {
}
