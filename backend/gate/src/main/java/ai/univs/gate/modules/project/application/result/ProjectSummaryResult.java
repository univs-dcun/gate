package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.enums.ProjectStatus;

import java.time.LocalDateTime;

public record ProjectSummaryResult(
        Long projectId,
        String projectName,
        String projectDescription,
        String colorTag,
        ProjectStatus status,
        Long countFaceRegistration,
        Long countFaceVerifyById,
        Long countFaceVerifyByImage,
        Long countFaceIdentify,
        Long countFaceLiveness,
        Long countPalmRegistration,
        Long countPalmIdentify,
        Long countPalmLiveness,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String apiKey
) {}
