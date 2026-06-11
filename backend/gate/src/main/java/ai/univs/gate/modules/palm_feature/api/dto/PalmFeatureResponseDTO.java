package ai.univs.gate.modules.palm_feature.api.dto;

import ai.univs.gate.modules.palm_feature.application.result.PalmFeatureResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record PalmFeatureResponseDTO(
        @Schema(description = SwaggerDescriptions.PALM_FEATURE_ID)
        Long palmFeatureId,

        @Schema(description = SwaggerDescriptions.PALM_DESCRIPTION)
        String description,


        @Schema(description = SwaggerDescriptions.PALM_FEATURE_AI_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.FEATURE_IMAGE_PATH)
        String featureImagePath,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.REGISTERED_AT)
        LocalDateTime createdAt,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public static PalmFeatureResponseDTO from(PalmFeatureResult result, String timezone) {
        return new PalmFeatureResponseDTO(
                result.palmFeatureId(),
                result.description(),
                result.featureId(),
                result.featureImagePath(),
                result.checkLiveness(),
                fromUtc(result.createdAt(), timezone),
                result.transactionUuid());
    }
}
