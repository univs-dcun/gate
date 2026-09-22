package ai.univs.gate.modules.feature.application.result.match;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.domain.enums.ActivitySource;
import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.shared.utils.ImagePathUtil;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MatchHistoryResult(
        Long matchingHistoryId,
        Long sequence,
        Long projectId,
        FeatureType featureType,
        ActivityType matchType,
        ActivitySource source,
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
        LocalDateTime createdAt,
        String externalKey
) {

    /**
     * UG-326: 원본이 match_history 든 feature_history 든 같은 형태로 내려준다. matchingHistoryId 는 원
     * 테이블의 숫자 id 이고, 어느 테이블인지는 source 가 말한다.
     */
    public static MatchHistoryResult from(ActivityLog log, String prefixImagePath, boolean consentEnabled) {
        return new MatchHistoryResult(
                log.getSourceId(),
                log.getId(),
                log.getProjectId(),
                log.getFeatureType(),
                log.getActivityType(),
                log.getSource(),
                log.getEventTime(),
                log.getCheckLiveness(),
                log.getSuccess(),
                log.getFeatureId(),
                log.getFeatureSeq(),
                log.getUserDescription(),
                log.getSimilarity(),
                ImagePathUtil.get(consentEnabled, prefixImagePath, log.getFeatureImagePath()),
                ImagePathUtil.get(consentEnabled, prefixImagePath, log.getMatchedFeatureImagePath()),
                log.getFailureType(),
                log.getTransactionUuid(),
                log.getConsentSnapshot(),
                log.getCreatedAt(),
                log.getExternalKey());
    }
}
