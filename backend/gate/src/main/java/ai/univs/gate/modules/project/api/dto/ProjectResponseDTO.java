package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.enums.ProjectType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record ProjectResponseDTO(
        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,
        @Schema(description = SwaggerDescriptions.PROJECT_NAME)
        String projectName,
        @Schema(description = SwaggerDescriptions.PROJECT_DESCRIPTION)
        String projectDescription,
        @Schema(description = SwaggerDescriptions.PROJECT_TYPE)
        ProjectType projectType,
        @Schema(description = SwaggerDescriptions.PROJECT_MODULE_TYPE)
        ProjectModuleType projectModuleType,
        @Schema(description = SwaggerDescriptions.PACKAGE_KEY)
        String packageKey,
        @Schema(description = SwaggerDescriptions.COUNT_USER_REGISTRATION)
        Long countUserRegistration,
        @Schema(description = SwaggerDescriptions.COUNT_VERIFY_BY_ID)
        Long countVerifyById,
        @Schema(description = SwaggerDescriptions.COUNT_VERIFY_BY_IMAGE)
        Long countVerifyByImage,
        @Schema(description = SwaggerDescriptions.COUNT_IDENTIFY)
        Long countIdentify,
        @Schema(description = SwaggerDescriptions.COUNT_LIVENESS)
        Long countLiveness,
        @Schema(description = SwaggerDescriptions.PROJECT_STATUS)
        ProjectStatus status,
        @Schema(description = SwaggerDescriptions.API_KEY)
        String apiKey,
        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt,
        @Schema(description = SwaggerDescriptions.UPDATED_AT)
        LocalDateTime updatedAt
) {

    public static ProjectResponseDTO from(ProjectSummaryResult result, String timezone) {
        return new ProjectResponseDTO(
                result.projectId(),
                result.projectName(),
                result.projectDescription(),
                result.projectType(),
                result.projectModuleType(),
                result.packageKey(),
                result.countUserRegistration(),
                result.countVerifyById(),
                result.countVerifyByImage(),
                result.countIdentify(),
                result.countLiveness(),
                result.status(),
                result.apiKey(),
                fromUtc(result.createdAt(), timezone),
                fromUtc(result.updatedAt(), timezone));
    }

    public static ProjectResponseDTO from(ProjectResult result, String timezone) {
        return new ProjectResponseDTO(
                result.projectId(),
                result.projectName(),
                result.projectDescription(),
                result.projectType(),
                result.projectModuleType(),
                result.packageKey(),
                null, null, null, null, null,
                result.status(),
                result.apiKey(),
                fromUtc(result.createdAt(), timezone),
                fromUtc(result.updatedAt(), timezone));
    }
}
