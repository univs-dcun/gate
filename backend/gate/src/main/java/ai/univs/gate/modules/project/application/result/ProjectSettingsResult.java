package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectType;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record ProjectSettingsResult(
        Long projectSettingsId,
        Long projectId,
        String projectName,
        ProjectType projectType,
        ProjectModuleType projectModuleType,
        String packageKey,
        Boolean consentEnabled,
        LocalDateTime consentAgreedAt,
        Boolean demoEnabled,
        Boolean sdkEnabled,
        Boolean livenessRecordingEnabled,
        Boolean livenessIdentifyingEnabled,
        Boolean livenessVerifyingByIdEnabled,
        Boolean livenessVerifyingByImageEnabled
) {

    public static ProjectSettingsResult from(ProjectSettings settings, String timezone) {
        return new ProjectSettingsResult(
                settings.getId(),
                settings.getProject().getId(),
                settings.getProject().getProjectName(),
                settings.getProject().getProjectType(),
                settings.getProject().getProjectModuleType(),
                settings.getProject().getPackageKey(),
                settings.getConsentEnabled(),
                fromUtc(settings.getConsentAgreedAt(), timezone),
                settings.getDemoEnabled(),
                settings.getSdkEnabled(),
                settings.getLivenessRecordingEnabled(),
                settings.getLivenessIdentifyingEnabled(),
                settings.getLivenessVerifyingByIdEnabled(),
                settings.getLivenessVerifyingByImageEnabled());
    }
}
