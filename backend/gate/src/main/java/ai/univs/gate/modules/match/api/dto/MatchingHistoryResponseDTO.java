package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.match.application.result.MatchHistoryResult;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record MatchingHistoryResponseDTO(
        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_ID)
        Long matchingHistoryId,

        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,

        @Schema(description = SwaggerDescriptions.FEATURE_TYPE)
        FeatureType featureType,

        @Schema(description = SwaggerDescriptions.MATCHING_TYPE)
        MatchType matchType,

        @Schema(description = SwaggerDescriptions.MATCHING_TIME)
        LocalDateTime matchingTime,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.MATCHING_SUCCESS)
        Boolean success,

        @Schema(description = SwaggerDescriptions.FEATURE_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.FEATURE_SEQ_ID)
        Long featureSeq,

        @Schema(description = SwaggerDescriptions.FEATURE_DESCRIPTION)
        String description,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        BigDecimal similarity,

        @Schema(description = SwaggerDescriptions.FEATURE_IMAGE_PATH)
        String featureImagePath,

        @Schema(description = SwaggerDescriptions.MATCHED_FEATURE_IMAGE_PATH)
        String matchingFeatureImagePath,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_TYPE)
        String failureType,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CONSENT_ENABLED)
        Boolean consentSnapshot,

        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt
) {

    public static MatchingHistoryResponseDTO from(MatchHistoryResult result,
                                                  String failureReason,
                                                  String timezone
    ) {
        return new MatchingHistoryResponseDTO(
                result.matchingHistoryId(),
                result.projectId(),
                result.featureType(),
                result.matchType(),
                fromUtc(result.matchingTime(), timezone),
                result.checkLiveness(),
                result.success(),
                result.featureId(),
                result.featureSeq(),
                result.description(),
                result.similarity(),
                result.featureImagePath(),
                result.matchingFeatureImagePath(),
                result.failureType(),
                failureReason,
                result.transactionUuid(),
                result.consentSnapshot(),
                fromUtc(result.createdAt(), timezone));
    }
}
