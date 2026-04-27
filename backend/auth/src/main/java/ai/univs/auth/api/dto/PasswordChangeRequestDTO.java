package ai.univs.auth.api.dto;

import ai.univs.auth.application.input.PasswordChangeInput;
import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record PasswordChangeRequestDTO(
        @Schema(description = SwaggerDescriptions.ACCOUNT_ID, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "REQUIRED_ACCOUNT_ID")
        @Min(value = 0, message = "INVALID_ACCOUNT_ID")
        @Max(value = Long.MAX_VALUE, message = "INVALID_ACCOUNT_ID")
        Long accountId,

        @Schema(description = SwaggerDescriptions.PASSWORD, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_PASSWORD")
        @Length(max = 255, message = "INVALID_PASSWORD_LENGTH")
        String password,

        @Schema(description = SwaggerDescriptions.NEW_PASSWORD, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_NEW_PASSWORD")
        @Length(max = 255, message = "INVALID_NEW_PASSWORD_LENGTH")
        String newPassword
) {

    public PasswordChangeInput toPasswordChangeInput() {
        return new PasswordChangeInput(accountId, password, newPassword);
    }
}
