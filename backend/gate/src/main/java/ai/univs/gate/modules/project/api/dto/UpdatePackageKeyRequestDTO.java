package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record UpdatePackageKeyRequestDTO(
        @Schema(description = SwaggerDescriptions.PACKAGE_KEY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_PACKAGE_KEY")
        @Length(max = 99, message = "INVALID_PACKAGE_KEY_LENGTH")
        String packageKey
) {
}
