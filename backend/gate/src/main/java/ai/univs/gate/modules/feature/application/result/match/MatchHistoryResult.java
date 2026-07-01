package ai.univs.gate.modules.feature.application.result.match;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.shared.utils.ImagePathUtil;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MatchHistoryResult(
        Long matchingHistoryId,
        Long projectId,
        FeatureType featureType,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean checkLiveness,
        Boolean success,
        String featureId,
        Long featureSeq,
        String description,
        BigDecimal similarity,
        String featureImagePath,
        String matchingFeatureImagePath,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot,
        LocalDateTime createdAt
) {

    public static MatchHistoryResult from(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new MatchHistoryResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getFeatureType(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFeatureId(),
                matchHistory.getFeatureSeq(),
                matchHistory.getUserDescription(),
                matchHistory.getSimilarity(),
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getFeatureImagePath()),
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getMatchedFeatureImagePath()),
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot(),
                matchHistory.getCreatedAt());
    }
}
