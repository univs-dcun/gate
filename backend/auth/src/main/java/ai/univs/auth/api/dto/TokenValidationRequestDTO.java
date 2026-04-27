package ai.univs.auth.api.dto;

import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record TokenValidationRequestDTO(
        @Schema(description = SwaggerDescriptions.ACCESS_TOKEN, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_ACCESS_TOKEN")
        @Length(max = 2048, message = "INVALID_ACCESS_TOKEN_LENGTH")
        String accessToken
) {
}
