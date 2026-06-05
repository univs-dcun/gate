package ai.univs.gate.modules.face_feature.application.result;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import org.springframework.util.StringUtils;

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

    public static VerifyByImageResult failResult(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new VerifyByImageResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
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

    public static VerifyByImageResult successResult(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new VerifyByImageResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFeatureId(),
                matchHistory.getSimilarity(),
                consentEnabled && StringUtils.hasText(matchHistory.getFeatureImagePath())
                        ? prefixImagePath + matchHistory.getFeatureImagePath()
                        : "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchedFeatureImagePath())
                        ? prefixImagePath + matchHistory.getMatchedFeatureImagePath()
                        : "",
                "",
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }
}
