package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
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
        @Schema(description = SwaggerDescriptions.COLOR_TAG)
        String colorTag,
        @Schema(description = SwaggerDescriptions.FACE_COUNT)
        FaceCountDTO face,
        @Schema(description = SwaggerDescriptions.PALM_COUNT)
        PalmCountDTO palm,
        @Schema(description = SwaggerDescriptions.PROJECT_STATUS)
        ProjectStatus status,
        @Schema(description = SwaggerDescriptions.API_KEY)
        String apiKey,
        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt,
        @Schema(description = SwaggerDescriptions.UPDATED_AT)
        LocalDateTime updatedAt
) {

    public record FaceCountDTO(
            @Schema(description = SwaggerDescriptions.FACE_COUNT_REGISTRATION)
            Long countRegistration,
            @Schema(description = SwaggerDescriptions.FACE_COUNT_VERIFY_BY_ID)
            Long countVerifyById,
            @Schema(description = SwaggerDescriptions.FACE_COUNT_VERIFY_BY_IMAGE)
            Long countVerifyByImage,
            @Schema(description = SwaggerDescriptions.FACE_COUNT_IDENTIFY)
            Long countIdentify,
            @Schema(description = SwaggerDescriptions.FACE_COUNT_LIVENESS)
            Long countLiveness
    ) {}

    public record PalmCountDTO(
            @Schema(description = SwaggerDescriptions.PALM_COUNT_REGISTRATION)
            Long countRegistration,
            @Schema(description = SwaggerDescriptions.PALM_COUNT_IDENTIFY)
            Long countIdentify,
            @Schema(description = SwaggerDescriptions.PALM_COUNT_LIVENESS)
            Long countLiveness
    ) {}

    public static ProjectResponseDTO from(ProjectSummaryResult result, String timezone) {
        FaceCountDTO face = new FaceCountDTO(
                result.countFaceRegistration(),
                result.countFaceVerifyById(),
                result.countFaceVerifyByImage(),
                result.countFaceIdentify(),
                result.countFaceLiveness());
        PalmCountDTO palm = new PalmCountDTO(
                result.countPalmRegistration(),
                result.countPalmIdentify(),
                result.countPalmLiveness());
        return new ProjectResponseDTO(
                result.projectId(),
                result.projectName(),
                result.projectDescription(),
                result.colorTag(),
                face,
                palm,
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
                result.colorTag(),
                null,
                null,
                result.status(),
                result.apiKey(),
                fromUtc(result.createdAt(), timezone),
                fromUtc(result.updatedAt(), timezone));
    }
}
