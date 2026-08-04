package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.result.face.IdentifyByDescriptorResult;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

/**
 * descriptor 기반 1:N 매칭 응답 (UG-279).
 *
 * <p>{@link IdentifyResponseDTO} 와 분리한 이유는 {@link IdentifyByDescriptorResult} 주석 참고.
 */
public record IdentifyByDescriptorResponseDTO(
        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_ID)
        Long matchingHistoryId,

        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,

        @Schema(description = SwaggerDescriptions.MATCHING_TYPE)
        MatchType matchType,

        @Schema(description = SwaggerDescriptions.MATCHING_TIME)
        LocalDateTime matchingTime,

        @Schema(description = SwaggerDescriptions.MATCHING_SUCCESS)
        Boolean success,

        @Schema(description = SwaggerDescriptions.FEATURE_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        BigDecimal similarity,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_TYPE)
        String failureType,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public static IdentifyByDescriptorResponseDTO from(IdentifyByDescriptorResult result,
                                                       String failureReason,
                                                       String timezone
    ) {
        return new IdentifyByDescriptorResponseDTO(
                result.matchingHistoryId(),
                result.projectId(),
                result.matchType(),
                fromUtc(result.matchingTime(), timezone),
                result.success(),
                result.featureId(),
                result.similarity(),
                result.failureType(),
                failureReason,
                result.transactionUuid());
    }
}
