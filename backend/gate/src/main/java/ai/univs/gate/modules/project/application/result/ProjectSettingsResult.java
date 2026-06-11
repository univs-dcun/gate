package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;

import java.time.LocalDateTime;
import java.util.List;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record ProjectSettingsResult(
        Long projectSettingsId,
        Long projectId,
        String projectName,
        Boolean consentEnabled,
        LocalDateTime consentAgreedAt,
        List<LivenessSettingResult> livenessSettings
) {

    public static ProjectSettingsResult from(ProjectSettings settings, List<ProjectLivenessSetting> livenessSettings, String timezone) {
        return new ProjectSettingsResult(
                settings.getId(),
                settings.getProject().getId(),
                settings.getProject().getProjectName(),
                settings.getConsentEnabled(),
                fromUtc(settings.getConsentAgreedAt(), timezone),
                livenessSettings.stream().map(LivenessSettingResult::from).toList());
    }
}
