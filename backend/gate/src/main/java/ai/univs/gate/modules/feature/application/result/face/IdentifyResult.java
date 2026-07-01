package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.shared.utils.ImagePathUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IdentifyResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean checkLiveness,
        Boolean success,
        String featureId,
        String description,
        BigDecimal similarity,
        String featureImagePath,
        String matchingFeatureImagePath,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static IdentifyResult failResult(MatchHistory matchHistory,
                                            String prefixImagePath,
                                            boolean consentEnabled
    ) {
        return new IdentifyResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
                "",
                matchHistory.getSimilarity(),
                "",
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getMatchedFeatureImagePath()),
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }

    public static IdentifyResult successResult(MatchHistory matchHistory,
                                               String prefixImagePath,
                                               boolean consentEnabled
    ) {
        return new IdentifyResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFeatureId(),
                matchHistory.getUserDescription(),
                matchHistory.getSimilarity(),
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getFeatureImagePath()),
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getMatchedFeatureImagePath()),
                "",
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }
}
