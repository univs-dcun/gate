package ai.univs.gate.modules.face_feature.application.result;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import org.springframework.util.StringUtils;

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
        String username,
        BigDecimal similarity,
        String matchingFeatureId,
        String featureImagePath,
        String matchingFeatureImagePath,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static VerifyByFaceIdResult failResult(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new VerifyByFaceIdResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
                "",
                "",
                matchHistory.getSimilarity(),
                matchHistory.getMatchedFeatureId(),
                "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchedFeatureImagePath())
                        ? prefixImagePath + matchHistory.getMatchedFeatureImagePath()
                        : "",
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }

    public static VerifyByFaceIdResult successResult(MatchHistory history, String prefixImagePath, boolean consentEnabled) {
        return new VerifyByFaceIdResult(
                history.getId(),
                history.getProject().getId(),
                history.getMatchType(),
                history.getMatchTime(),
                history.getCheckLiveness(),
                history.getSuccess(),
                history.getFeatureId(),
                history.getUserDescription(),
                history.getUsername(),
                history.getSimilarity(),
                history.getMatchedFeatureId(),
                consentEnabled && StringUtils.hasText(history.getFeatureImagePath())
                        ? prefixImagePath + history.getFeatureImagePath()
                        : "",
                consentEnabled && StringUtils.hasText(history.getMatchedFeatureImagePath())
                        ? prefixImagePath + history.getMatchedFeatureImagePath()
                        : "",
                "",
                history.getTransactionUuid(),
                history.getConsentSnapshot());
    }
}
