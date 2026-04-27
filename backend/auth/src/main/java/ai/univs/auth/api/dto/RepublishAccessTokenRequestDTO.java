package ai.univs.auth.api.dto;

import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record RepublishAccessTokenRequestDTO(
        @Schema(description = SwaggerDescriptions.REFRESH_TOKEN, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_REFRESH_TOKEN")
        @Length(max = 255, message = "INVALID_REFRESH_TOKEN_LENGTH")
        String refreshToken
) {
}
