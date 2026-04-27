package ai.univs.gate.support.project;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectSettingsService {

    private final ProjectSettingsRepository projectSettingsRepository;

    public ProjectSettings findByProject(Project project) {
        return projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));
    }

    public void checkAvailabilityModules(CallerType callerType, ProjectSettings projectSettings) {
        switch (callerType) {
            case DEMO -> validateDemoEnabled(projectSettings);
            case SDK -> validateSdkEnabled(projectSettings);
        }
    }

    public void validateDemoEnabled(ProjectSettings settings) {
        if (!settings.getDemoEnabled()) {
            throw new CustomGateException(ErrorType.DEMO_DISABLED);
        }
    }

    public void validateSdkEnabled(ProjectSettings settings) {
        if (!settings.getSdkEnabled()) {
            throw new CustomGateException(ErrorType.SDK_DISABLED);
        }
    }
}
