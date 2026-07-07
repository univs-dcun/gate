package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record LivenessSettingsUpdateRequestDTO(
        @Schema(description = SwaggerDescriptions.FEATURE_TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        FeatureType moduleType,

        @Schema(description = SwaggerDescriptions.LIVENESS_SETTINGS)
        @NotNull
        List<OperationSettingDTO> settings
) {
    public record OperationSettingDTO(
            @Schema(description = SwaggerDescriptions.LIVENESS_OPERATION)
            LivenessOperation operation,
            @Schema(description = SwaggerDescriptions.LIVENESS_ENABLED)
            boolean enabled
    ) {}
}
