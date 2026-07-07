package ai.univs.gate.support.project;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.repository.ProjectLivenessSettingRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectSettingsService {

    private final ProjectSettingsRepository projectSettingsRepository;
    private final ProjectLivenessSettingRepository projectLivenessSettingRepository;

    public ProjectSettings findByProject(Project project) {
        return projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));
    }

    public boolean isLivenessEnabled(ProjectSettings settings, FeatureType moduleType, LivenessOperation operation) {
        return projectLivenessSettingRepository
                .findByProjectSettingsAndModuleTypeAndOperation(settings, moduleType, operation)
                .map(s -> Boolean.TRUE.equals(s.getEnabled()))
                .orElse(false);
    }
}
