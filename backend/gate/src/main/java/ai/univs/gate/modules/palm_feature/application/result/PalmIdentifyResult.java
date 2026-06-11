package ai.univs.gate.modules.palm_feature.application.result;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PalmIdentifyResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean checkLiveness,
        Boolean success,
        Long palmFeatureId,
        String featureId,
        String description,
        BigDecimal similarity,
        String featureImagePath,
        String matchingFeatureImagePath,
        String threshold,
        String failureType,
        String transactionUuid,
        Boolean consentSnapshot
) {

    public static PalmIdentifyResult failResult(MatchHistory matchHistory, String failureType,
                                                String prefixImagePath, boolean consentEnabled) {
        return new PalmIdentifyResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                false,
                null,
                null,
                "",
                matchHistory.getSimilarity(),
                "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchedFeatureImagePath())
                        ? prefixImagePath + matchHistory.getMatchedFeatureImagePath() : "",
                null,
                failureType,
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }

    public static PalmIdentifyResult successResult(MatchHistory matchHistory, PalmFeature palmFeature,
                                                   BigDecimal similarity, String threshold,
                                                   String prefixImagePath, boolean consentEnabled) {
        return new PalmIdentifyResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                true,
                palmFeature.getId(),
                palmFeature.getFeatureId(),
                matchHistory.getUserDescription(),
                similarity,
                consentEnabled && StringUtils.hasText(matchHistory.getFeatureImagePath())
                        ? prefixImagePath + matchHistory.getFeatureImagePath() : "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchedFeatureImagePath())
                        ? prefixImagePath + matchHistory.getMatchedFeatureImagePath() : "",
                threshold,
                null,
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }
}
