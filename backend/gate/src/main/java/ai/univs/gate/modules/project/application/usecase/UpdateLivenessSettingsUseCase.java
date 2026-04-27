package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.UpdateLivenessSettingsInput;
import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateLivenessSettingsUseCase {

    private final ProjectService projectService;
    private final ProjectSettingsRepository projectSettingsRepository;

    @Transactional
    public ProjectSettingsResult execute(UpdateLivenessSettingsInput input) {
        UserContext userContext = UserContext.get();

        Project project = projectService.validateOwnership(input.projectId(), userContext.getAccountIdAsLong());
        ProjectSettings settings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        settings.updateLivenessSettings(
                input.livenessRecordingEnabled(),
                input.livenessIdentifyingEnabled(),
                input.livenessVerifyingEnabled());

        log.info("Liveness settings updated: projectId={}", input.projectId());

        return ProjectSettingsResult.from(settings, userContext.getTimezone());
    }
}
