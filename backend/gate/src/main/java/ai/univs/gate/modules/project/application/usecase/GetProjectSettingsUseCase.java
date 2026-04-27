package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectSettingsUseCase {

    private final ProjectService projectService;
    private final ProjectSettingsRepository projectSettingsRepository;

    @Transactional(readOnly = true)
    public ProjectSettingsResult execute(Long projectId) {
        UserContext userContext = UserContext.get();

        Project project = projectService.validateOwnership(projectId, userContext.getAccountIdAsLong());

        ProjectSettings settings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        return ProjectSettingsResult.from(settings, userContext.getTimezone());
    }
}
