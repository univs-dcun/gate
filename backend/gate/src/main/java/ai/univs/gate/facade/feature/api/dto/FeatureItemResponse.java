package ai.univs.gate.facade.feature.api.dto;

import ai.univs.gate.facade.feature.application.result.FeatureItemResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record FeatureItemResponse(
        @Schema(description = SwaggerDescriptions.FEATURE_TYPE)
        String featureType,

        @Schema(description = SwaggerDescriptions.FEATURE_SEQ_ID)
        Long featureId,

        @Schema(description = SwaggerDescriptions.FEATURE_MEMO)
        String description,

        @Schema(description = SwaggerDescriptions.FEATURE_IMAGE_URL)
        String imageUrl,

        @Schema(description = SwaggerDescriptions.FEATURE_FID)
        String fid,

        @Schema(description = SwaggerDescriptions.REGISTERED_AT)
        LocalDateTime createdAt
) {

    public static FeatureItemResponse from(FeatureItemResult result, String timezone) {
        return new FeatureItemResponse(
                result.featureType(),
                result.featureId(),
                result.description(),
                result.imageUrl(),
                result.fid(),
                fromUtc(result.createdAt(), timezone));
    }
}
