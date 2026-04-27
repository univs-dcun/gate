package ai.univs.auth.api.dto;

import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record VerifyEmailCodeRequestDTO(
        @Schema(description = SwaggerDescriptions.EMAIL, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_EMAIL")
        @Length(max = 255, message = "INVALID_EMAIL_LENGTH")
        @Email(message = "INVALID_EMAIL_FORMAT")
        String email,

        @Schema(description = SwaggerDescriptions.CODE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_CODE")
        @Length(min = 8, max = 8, message = "INVALID_CODE_LENGTH")
        String code
) {
}
