package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.support.file.ImageUrls;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VerifyByFaceIdResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean checkLiveness,
        Boolean success,
        String featureId,
        String description,
        BigDecimal similarity,
        String matchingFeatureId,
        String featureImagePath,
        String matchingFeatureImagePath,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static VerifyByFaceIdResult failResult(MatchHistory matchHistory,
                                                  ImageUrls imageUrls
    ) {
        return new VerifyByFaceIdResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
                "",
                matchHistory.getSimilarity(),
                matchHistory.getFeatureId(),
                "",
                imageUrls.of(matchHistory.getMatchedFeatureImagePath()),
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }

    public static VerifyByFaceIdResult successResult(MatchHistory history,
                                                     ImageUrls imageUrls
    ) {
        return new VerifyByFaceIdResult(
                history.getId(),
                history.getProject().getId(),
                history.getMatchType(),
                history.getMatchTime(),
                history.getCheckLiveness(),
                history.getSuccess(),
                history.getFeatureId(),
                history.getUserDescription(),
                history.getSimilarity(),
                history.getFeatureId(),
                imageUrls.of(history.getFeatureImagePath()),
                imageUrls.of(history.getMatchedFeatureImagePath()),
                "",
                history.getTransactionUuid(),
                history.getConsentSnapshot());
    }
}
