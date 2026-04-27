package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.shared.billing.PlanType;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.enums.ProjectType;

import java.time.LocalDateTime;

public record ProjectSummaryResult(
        Long projectId,
        String projectName,
        String projectDescription,
        ProjectStatus status,
        ProjectType projectType,
        ProjectModuleType projectModuleType,
        String packageKey,
        PlanType planType,
        LocalDateTime planStartedAt,
        LocalDateTime planExpiredAt,
        Long userRegistrationAllocated,
        Long userRegistrationLimit,
        Long countUserRegistration,
        Long verifyLimit,
        Long verifyAllocated,
        Long countVerify,
        Long identifyLimit,
        Long identifyAllocated,
        Long countIdentify,
        Long livenessLimit,
        Long livenessAllocated,
        Long countLiveness,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String apiKey
) {}
