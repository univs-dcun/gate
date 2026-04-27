package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.shared.billing.PlanType;
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
        @Schema(description = SwaggerDescriptions.PROJECT_PLAN_TYPE)
        PlanType planType,
        @Schema(description = SwaggerDescriptions.PLAN_STARTED_AT)
        LocalDateTime planStartedAt,
        @Schema(description = SwaggerDescriptions.PROJECT_PLAN_EXPIRY)
        LocalDateTime planExpiredAt,
        @Schema(description = SwaggerDescriptions.USER_REGISTRATION_ALLOCATED)
        Long userRegistrationAllocated,
        @Schema(description = SwaggerDescriptions.USER_REGISTRATION_LIMIT)
        Long userRegistrationLimit,
        @Schema(description = SwaggerDescriptions.COUNT_USER_REGISTRATION)
        Long countUserRegistration,
        @Schema(description = SwaggerDescriptions.VERIFY_LIMIT)
        Long verifyLimit,
        @Schema(description = SwaggerDescriptions.VERIFY_ALLOCATED)
        Long verifyAllocated,
        @Schema(description = SwaggerDescriptions.COUNT_VERIFY)
        Long countVerify,
        @Schema(description = SwaggerDescriptions.IDENTIFY_LIMIT)
        Long identifyLimit,
        @Schema(description = SwaggerDescriptions.IDENTIFY_ALLOCATED)
        Long identifyAllocated,
        @Schema(description = SwaggerDescriptions.COUNT_IDENTIFY)
        Long countIdentify,
        @Schema(description = SwaggerDescriptions.LIVENESS_LIMIT)
        Long livenessLimit,
        @Schema(description = SwaggerDescriptions.LIVENESS_ALLOCATED)
        Long livenessAllocated,
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

    // 프로젝트 목록 조회용 (billing 집계 포함)
    public static ProjectResponseDTO from(ProjectSummaryResult result, String timezone) {
        return new ProjectResponseDTO(
                result.projectId(),
                result.projectName(),
                result.projectDescription(),
                result.projectType(),
                result.projectModuleType(),
                result.packageKey(),
                result.planType(),
                fromUtc(result.planStartedAt(), timezone),
                fromUtc(result.planExpiredAt(), timezone),
                result.userRegistrationAllocated(),
                result.userRegistrationLimit(),
                result.countUserRegistration(),
                result.verifyLimit(),
                result.verifyAllocated(),
                result.countVerify(),
                result.identifyLimit(),
                result.identifyAllocated(),
                result.countIdentify(),
                result.livenessLimit(),
                result.livenessAllocated(),
                result.countLiveness(),
                result.status(),
                result.apiKey(),
                fromUtc(result.createdAt(), timezone),
                fromUtc(result.updatedAt(), timezone));
    }

    // 단건 조회 / 생성 / 수정 응답용
    public static ProjectResponseDTO from(ProjectResult result, String timezone) {
        return new ProjectResponseDTO(
                result.projectId(),
                result.projectName(),
                result.projectDescription(),
                result.projectType(),
                result.projectModuleType(),
                result.packageKey(),
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                result.status(),
                result.apiKey(),
                fromUtc(result.createdAt(), timezone),
                fromUtc(result.updatedAt(), timezone));
    }
}
