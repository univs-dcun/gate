package ai.univs.gate.modules.palm_feature.api.dto;

import ai.univs.gate.modules.palm_feature.application.result.PalmIdentifyResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record PalmIdentifyResponseDTO(
        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_ID)
        Long matchingHistoryId,

        @Schema(description = SwaggerDescriptions.PALM_FEATURE_ID)
        Long palmFeatureId,

        @Schema(description = SwaggerDescriptions.PALM_FEATURE_AI_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.MATCHING_SUCCESS)
        Boolean success,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        BigDecimal similarity,

        @Schema(description = SwaggerDescriptions.THRESHOLD)
        String threshold,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_TYPE)
        String failureType,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.MATCHING_TIME)
        LocalDateTime matchingTime,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public static PalmIdentifyResponseDTO from(PalmIdentifyResult result, String failureReason, String timezone) {
        return new PalmIdentifyResponseDTO(
                result.matchingHistoryId(),
                result.palmFeatureId(),
                result.featureId(),
                result.success(),
                result.similarity(),
                result.threshold(),
                result.failureType(),
                failureReason,
                fromUtc(result.matchingTime(), timezone),
                result.transactionUuid());
    }
}
