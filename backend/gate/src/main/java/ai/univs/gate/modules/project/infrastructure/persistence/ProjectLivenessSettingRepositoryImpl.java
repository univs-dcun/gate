package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.repository.ProjectLivenessSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectLivenessSettingRepositoryImpl implements ProjectLivenessSettingRepository {

    private final ProjectLivenessSettingJpaRepository jpaRepository;

    @Override
    public void save(ProjectLivenessSetting setting) {
        jpaRepository.save(setting);
    }

    @Override
    public void saveAll(List<ProjectLivenessSetting> settings) {
        jpaRepository.saveAll(settings);
    }

    @Override
    public Optional<ProjectLivenessSetting> findByProjectSettingsAndModuleTypeAndOperation(
            ProjectSettings projectSettings, FeatureType moduleType, LivenessOperation operation) {
        return jpaRepository.findByProjectSettingsAndModuleTypeAndOperation(projectSettings, moduleType, operation);
    }

    @Override
    public List<ProjectLivenessSetting> findAllByProjectSettings(ProjectSettings projectSettings) {
        return jpaRepository.findAllByProjectSettings(projectSettings);
    }
}
