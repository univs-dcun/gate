package ai.univs.auth.api.dto;

import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record PasswordResetConfirmDTO(
        @Schema(description = SwaggerDescriptions.EMAIL, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_EMAIL")
        @Length(max = 255, message = "INVALID_EMAIL_LENGTH")
        @Email(message = "INVALID_EMAIL_FORMAT")
        String email,

        @Schema(description = SwaggerDescriptions.NEW_PASSWORD, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_NEW_PASSWORD")
        @Length(max = 255, message = "INVALID_NEW_PASSWORD_LENGTH")
        String newPassword,

        @Schema(description = SwaggerDescriptions.PASSWORD_CONFIRM, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_PASSWORD_CONFIRM")
        @Length(max = 255, message = "INVALID_PASSWORD_CONFIRM_LENGTH")
        String passwordConfirm
) {
}
