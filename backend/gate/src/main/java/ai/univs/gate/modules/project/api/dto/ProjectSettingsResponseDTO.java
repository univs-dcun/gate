package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ProjectSettingsResponseDTO(
        @Schema(description = SwaggerDescriptions.PROJECT_SETTINGS_ID)
        Long projectSettingsId,
        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,
        @Schema(description = SwaggerDescriptions.PROJECT_NAME)
        String projectName,
        @Schema(description = SwaggerDescriptions.PROJECT_TYPE)
        ProjectType projectType,
        @Schema(description = SwaggerDescriptions.PROJECT_MODULE_TYPE)
        ProjectModuleType projectModuleType,
        @Schema(description = SwaggerDescriptions.PACKAGE_KEY)
        String packageKey,
        @Schema(description = SwaggerDescriptions.CONSENT_ENABLED)
        Boolean consentEnabled,
        @Schema(description = SwaggerDescriptions.CONSENT_AGREED_AT)
        LocalDateTime consentAgreedAt,
        @Schema(description = SwaggerDescriptions.DEMO_ENABLED)
        Boolean demoEnabled,
        @Schema(description = SwaggerDescriptions.SDK_ENABLED)
        Boolean sdkEnabled,
        @Schema(description = SwaggerDescriptions.LIVENESS_RECORDING_ENABLED)
        Boolean livenessRecordingEnabled,
        @Schema(description = SwaggerDescriptions.LIVENESS_IDENTIFYING_ENABLED)
        Boolean livenessIdentifyingEnabled,
        @Schema(description = SwaggerDescriptions.LIVENESS_VERIFYING_BY_ID_ENABLED)
        Boolean livenessVerifyingByIdEnabled,
        @Schema(description = SwaggerDescriptions.LIVENESS_VERIFYING_BY_IMAGE_ENABLED)
        Boolean livenessVerifyingByImageEnabled
) {

    public static ProjectSettingsResponseDTO from(ProjectSettingsResult result) {
        return new ProjectSettingsResponseDTO(
                result.projectSettingsId(),
                result.projectId(),
                result.projectName(),
                result.projectType(),
                result.projectModuleType(),
                result.packageKey(),
                result.consentEnabled(),
                result.consentAgreedAt(),
                result.demoEnabled(),
                result.sdkEnabled(),
                result.livenessRecordingEnabled(),
                result.livenessIdentifyingEnabled(),
                result.livenessVerifyingByIdEnabled(),
                result.livenessVerifyingByImageEnabled());
    }
}
