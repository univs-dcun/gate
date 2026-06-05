package ai.univs.gate.modules.match.application.result;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MatchHistoryResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean checkLiveness,
        Boolean success,
        String featureId,
        String description,
        String username,
        BigDecimal similarity,
        String featureImagePath,
        String matchingFeatureImagePath,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static MatchHistoryResult from(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new MatchHistoryResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFeatureId(),
                matchHistory.getUserDescription(),
                matchHistory.getUsername(),
                matchHistory.getSimilarity(),
                consentEnabled && StringUtils.hasText(matchHistory.getFeatureImagePath())
                        ? prefixImagePath + matchHistory.getFeatureImagePath()
                        : "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchedFeatureImagePath())
                        ? prefixImagePath + matchHistory.getMatchedFeatureImagePath()
                        : "",
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }
}
