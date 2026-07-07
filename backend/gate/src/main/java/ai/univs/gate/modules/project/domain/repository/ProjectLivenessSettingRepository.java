package ai.univs.gate.modules.project.domain.repository;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import java.util.List;
import java.util.Optional;

public interface ProjectLivenessSettingRepository {

    void save(ProjectLivenessSetting setting);

    void saveAll(List<ProjectLivenessSetting> settings);

    Optional<ProjectLivenessSetting> findByProjectSettingsAndModuleTypeAndOperation(
            ProjectSettings projectSettings, FeatureType moduleType, LivenessOperation operation);

    List<ProjectLivenessSetting> findAllByProjectSettings(ProjectSettings projectSettings);
}
