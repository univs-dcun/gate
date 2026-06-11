package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

public record LivenessSettingResult(
        FeatureType moduleType,
        LivenessOperation operation,
        Boolean enabled
) {
    public static LivenessSettingResult from(ProjectLivenessSetting setting) {
        return new LivenessSettingResult(
                setting.getModuleType(),
                setting.getOperation(),
                setting.getEnabled());
    }
}
