package ai.univs.match.api.dto;

import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_DESCRIPTOR")
        String descriptor,

        @Schema(description = SwaggerDescriptions.TARGET_DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_TARGET_DESCRIPTOR")
        String targetDescriptor
) {
}
