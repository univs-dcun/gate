package ai.univs.gate.modules.feature.application.result.palm;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.shared.utils.ImagePathUtil;

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

    public static PalmIdentifyResult failResult(MatchHistory matchHistory,
                                                String failureType,
                                                String prefixImagePath,
                                                boolean consentEnabled
    ) {
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
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getMatchedFeatureImagePath()),
                null,
                failureType,
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }

    public static PalmIdentifyResult successResult(MatchHistory matchHistory,
                                                   BiometricFeature biometricFeature,
                                                   BigDecimal similarity,
                                                   String threshold,
                                                   String prefixImagePath,
                                                   boolean consentEnabled
    ) {
        return new PalmIdentifyResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                true,
                biometricFeature.getId(),
                biometricFeature.getFeatureId(),
                matchHistory.getUserDescription(),
                similarity,
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getFeatureImagePath()),
                ImagePathUtil.get(consentEnabled, prefixImagePath, matchHistory.getMatchedFeatureImagePath()),
                threshold,
                null,
                matchHistory.getTransactionUuid(),
                matchHistory.getConsentSnapshot());
    }
}
