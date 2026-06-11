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
        Long featureSeq,

        @Schema(description = SwaggerDescriptions.FEATURE_MEMO)
        String description,

        @Schema(description = SwaggerDescriptions.FEATURE_IMAGE_URL)
        String imageUrl,

        @Schema(description = SwaggerDescriptions.FEATURE_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.REGISTERED_AT)
        LocalDateTime createdAt
) {

    public static FeatureItemResponse from(FeatureItemResult result, String timezone) {
        return new FeatureItemResponse(
                result.featureType(),
                result.featureSeq(),
                result.description(),
                result.imageUrl(),
                result.featureId(),
                fromUtc(result.createdAt(), timezone));
    }
}
