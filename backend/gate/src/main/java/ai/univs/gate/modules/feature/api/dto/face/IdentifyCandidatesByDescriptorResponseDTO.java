package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.result.face.IdentifyCandidatesByDescriptorResult;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

/**
 * 특징점 기반 1:N 후보 목록 매칭 응답 (UG-314).
 *
 * <p>{@link IdentifyByDescriptorResponseDTO} 의 {@code featureId}/{@code similarity} 자리에
 * {@code candidates} 목록이 들어간다. 나머지는 같다.
 */
public record IdentifyCandidatesByDescriptorResponseDTO(
        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_ID)
        Long matchingHistoryId,

        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,

        @Schema(description = SwaggerDescriptions.MATCHING_TYPE)
        MatchType matchType,

        @Schema(description = SwaggerDescriptions.MATCHING_TIME)
        LocalDateTime matchingTime,

        @Schema(description = "후보가 1명 이상이면 true")
        Boolean success,

        @Schema(description = "임계값을 넘은 후보 목록. 유사도가 높은 순이며 비어 있을 수 있다")
        List<Candidate> candidates,

        @Schema(description = SwaggerDescriptions.MATCH_THRESHOLD)
        BigDecimal threshold,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_TYPE)
        String failureType,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public record Candidate(
            @Schema(description = SwaggerDescriptions.FEATURE_ID)
            String featureId,

            @Schema(description = SwaggerDescriptions.FEATURE_DESCRIPTION)
            String userDescription,

            @Schema(description = SwaggerDescriptions.SIMILARITY)
            BigDecimal similarity
    ) {
    }

    public static IdentifyCandidatesByDescriptorResponseDTO from(
            IdentifyCandidatesByDescriptorResult result, String failureReason, String timezone) {
        return new IdentifyCandidatesByDescriptorResponseDTO(
                result.matchingHistoryId(),
                result.projectId(),
                result.matchType(),
                fromUtc(result.matchingTime(), timezone),
                result.success(),
                result.candidates().stream()
                        .map(candidate -> new Candidate(
                                candidate.featureId(),
                                candidate.userDescription(),
                                candidate.similarity()))
                        .toList(),
                result.threshold(),
                result.failureType(),
                failureReason,
                result.transactionUuid());
    }
}
