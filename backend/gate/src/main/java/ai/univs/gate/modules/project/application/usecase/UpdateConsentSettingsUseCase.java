package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.entity.ConsentLog;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ConsentLogRepository;
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
public class UpdateConsentSettingsUseCase {

    private final ProjectService projectService;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ConsentLogRepository consentLogRepository;
    private final ProjectLivenessSettingRepository livenessSettingRepository;

    @Transactional
    public ProjectSettingsResult execute(Long projectId, boolean consentEnabled, String ipAddress) {
        UserContext userContext = UserContext.get();

        Project project = projectService.validateOwnership(projectId, userContext.getAccountIdAsLong());
        ProjectSettings settings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        settings.updateConsentSettings(consentEnabled);

        consentLogRepository.save(ConsentLog.builder()
                .project(project)
                .endUserIdentifier(userContext.getAccountIdAsLong())
                .consentType("PRIVACY")
                .agreed(consentEnabled)
                .ipAddress(ipAddress)
                .agreedAt(consentEnabled ? settings.getConsentAgreedAt() : null)
                .build());

        log.info("Consent settings updated: projectId={}, enabled={}", projectId, consentEnabled);

        var livenessSettings = livenessSettingRepository.findAllByProjectSettings(settings);
        return ProjectSettingsResult.from(settings, livenessSettings, userContext.getTimezone());
    }
}
