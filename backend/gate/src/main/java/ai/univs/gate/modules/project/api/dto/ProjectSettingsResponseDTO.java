package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.application.result.LivenessSettingResult;
import ai.univs.gate.modules.project.application.result.ProjectSettingsResult;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectSettingsResponseDTO(
        @Schema(description = SwaggerDescriptions.PROJECT_SETTINGS_ID)
        Long projectSettingsId,
        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,
        @Schema(description = SwaggerDescriptions.PROJECT_NAME)
        String projectName,
        @Schema(description = SwaggerDescriptions.PROJECT_TYPE)
        ProjectType projectType,
        @Schema(description = SwaggerDescriptions.PACKAGE_KEY)
        String packageKey,
        @Schema(description = SwaggerDescriptions.CONSENT_ENABLED)
        Boolean consentEnabled,
        @Schema(description = SwaggerDescriptions.CONSENT_AGREED_AT)
        LocalDateTime consentAgreedAt,
        @Schema(description = SwaggerDescriptions.LIVENESS_SETTINGS)
        List<LivenessSettingResponse> livenessSettings
) {

    public record LivenessSettingResponse(
            @Schema(description = SwaggerDescriptions.FEATURE_TYPE)
            FeatureType moduleType,
            @Schema(description = SwaggerDescriptions.LIVENESS_OPERATION)
            LivenessOperation operation,
            @Schema(description = SwaggerDescriptions.LIVENESS_ENABLED)
            Boolean enabled
    ) {
        public static LivenessSettingResponse from(LivenessSettingResult result) {
            return new LivenessSettingResponse(result.moduleType(), result.operation(), result.enabled());
        }
    }

    public static ProjectSettingsResponseDTO from(ProjectSettingsResult result) {
        return new ProjectSettingsResponseDTO(
                result.projectSettingsId(),
                result.projectId(),
                result.projectName(),
                result.projectType(),
                result.packageKey(),
                result.consentEnabled(),
                result.consentAgreedAt(),
                result.livenessSettings().stream().map(LivenessSettingResponse::from).toList());
    }
}
