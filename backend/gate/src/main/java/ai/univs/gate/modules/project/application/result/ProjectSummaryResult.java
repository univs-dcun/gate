package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.enums.ProjectStatus;

import java.time.LocalDateTime;

public record ProjectSummaryResult(
        Long projectId,
        String projectName,
        String projectDescription,
        ProjectStatus status,
        Long countUserRegistration,
        Long countVerifyById,
        Long countVerifyByImage,
        Long countIdentify,
        Long countLiveness,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String apiKey
) {}
