package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectLivenessSettingJpaRepository extends JpaRepository<ProjectLivenessSetting, Long> {

    Optional<ProjectLivenessSetting> findByProjectSettingsAndModuleTypeAndOperation(
            ProjectSettings projectSettings, FeatureType moduleType, LivenessOperation operation);

    List<ProjectLivenessSetting> findAllByProjectSettings(ProjectSettings projectSettings);
}
