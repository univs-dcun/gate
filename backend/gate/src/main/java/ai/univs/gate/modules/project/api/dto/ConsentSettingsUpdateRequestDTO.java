package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConsentSettingsUpdateRequestDTO(
        @Schema(description = SwaggerDescriptions.CONSENT_ENABLED, requiredMode = Schema.RequiredMode.REQUIRED)
        boolean consentEnabled
) {
}
