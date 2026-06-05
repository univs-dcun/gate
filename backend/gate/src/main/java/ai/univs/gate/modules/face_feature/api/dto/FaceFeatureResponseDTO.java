package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.modules.face_feature.application.result.FaceFeatureResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record FaceFeatureResponseDTO(
        @Schema(description = SwaggerDescriptions.FACE_FEATURE_ID)
        Long faceFeatureId,

        @Schema(description = SwaggerDescriptions.FACE_FEATURE_DESCRIPTION)
        String description,

        @Schema(description = SwaggerDescriptions.USERNAME)
        String username,

        @Schema(description = SwaggerDescriptions.FEATURE_AI_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.FACE_IMAGE_PATH)
        String featureImagePath,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public static FaceFeatureResponseDTO from(FaceFeatureResult result, String timezone) {
        return new FaceFeatureResponseDTO(
                result.faceFeatureId(),
                result.description(),
                result.username(),
                result.featureId(),
                result.featureImagePath(),
                result.checkLiveness(),
                fromUtc(result.createdAt(), timezone),
                result.transactionUuid());
    }
}
