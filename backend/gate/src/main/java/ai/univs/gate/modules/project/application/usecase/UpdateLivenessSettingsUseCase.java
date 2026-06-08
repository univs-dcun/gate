package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.UpdateLivenessSettingsInput;
import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectLivenessSettingRepository;
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
    private final ProjectLivenessSettingRepository livenessSettingRepository;

    @Transactional
    public ProjectSettingsResult execute(UpdateLivenessSettingsInput input) {
        UserContext userContext = UserContext.get();

        var project = projectService.validateOwnership(input.projectId(), userContext.getAccountIdAsLong());
        ProjectSettings settings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        input.settings().forEach(s -> {
            var existing = livenessSettingRepository
                    .findByProjectSettingsAndModuleTypeAndOperation(settings, input.moduleType(), s.operation());
            if (existing.isPresent()) {
                existing.get().updateEnabled(s.enabled());
            } else {
                livenessSettingRepository.save(ProjectLivenessSetting.builder()
                        .projectSettings(settings)
                        .moduleType(input.moduleType())
                        .operation(s.operation())
                        .enabled(s.enabled())
                        .build());
            }
        });

        log.info("Liveness settings updated: projectId={}, moduleType={}", input.projectId(), input.moduleType());

        var livenessSettings = livenessSettingRepository.findAllByProjectSettings(settings);
        return ProjectSettingsResult.from(settings, livenessSettings, userContext.getTimezone());
    }
}
