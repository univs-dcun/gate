package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record LivenessSettingsUpdateRequestDTO(
        @Schema(description = SwaggerDescriptions.LIVENESS_RECORDING_ENABLED)
        boolean livenessRecordingEnabled,

        @Schema(description = SwaggerDescriptions.LIVENESS_IDENTIFYING_ENABLED)
        boolean livenessIdentifyingEnabled,

        @Schema(description = SwaggerDescriptions.LIVENESS_VERIFYING_BY_ID_ENABLED)
        boolean livenessVerifyingByIdEnabled,

        @Schema(description = SwaggerDescriptions.LIVENESS_VERIFYING_BY_IMAGE_ENABLED)
        boolean livenessVerifyingByImageEnabled
) {
}
