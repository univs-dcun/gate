package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.support.file.ImageUrls;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VerifyByImageResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean checkLiveness,
        Boolean success,
        String featureId,
        BigDecimal similarity,
        String matchingFeatureImagePath,
        String targetMatchingFeatureImagePath,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static VerifyByImageResult failResult(MatchHistory matchHistory, ImageUrls imageUrls) {
        return new VerifyByImageResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
                matchHistory.getSimilarity(),
                imageUrls.of(matchHistory.getFeatureImagePath()),
                imageUrls.of(matchHistory.getMatchedFeatureImagePath()),
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }

    public static VerifyByImageResult successResult(MatchHistory matchHistory, ImageUrls imageUrls) {
        return new VerifyByImageResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFeatureId(),
                matchHistory.getSimilarity(),
                imageUrls.of(matchHistory.getFeatureImagePath()),
                imageUrls.of(matchHistory.getMatchedFeatureImagePath()),
                "",
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }
}
