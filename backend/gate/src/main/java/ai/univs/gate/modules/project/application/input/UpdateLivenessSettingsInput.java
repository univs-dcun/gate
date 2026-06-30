package ai.univs.gate.modules.project.application.input;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import java.util.List;

public record UpdateLivenessSettingsInput(
        Long projectId,
        FeatureType moduleType,
        List<OperationSetting> settings
) {
    public record OperationSetting(
            LivenessOperation operation,
            boolean enabled
    ) {}
}
