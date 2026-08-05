package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * descriptor 기반 1:N 매칭 결과 (UG-279).
 *
 * <p>{@link IdentifyResult} 와 분리한 이유는 descriptor 경로에 존재할 수 없거나 의미가 없는 필드를
 * 응답에서 빼기 위해서다 — {@code featureImagePath}/{@code matchingFeatureImagePath}(이미지 파일이
 * 없다), {@code description}(받지 않는다), {@code checkLiveness}(무조건 OFF),
 * {@code consentSnapshot}(보관할 이미지가 없어 동의 여부가 응답에서 의미를 갖지 않는다. 값 자체는
 * 이력에 계속 저장한다).
 */
public record IdentifyByDescriptorResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean success,
        String featureId,
        BigDecimal similarity,
        String failureType,
        String transactionUuid
) {

    public static IdentifyByDescriptorResult failResult(MatchHistory matchHistory) {
        return new IdentifyByDescriptorResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getSuccess(),
                "",
                matchHistory.getSimilarity(),
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid());
    }

    public static IdentifyByDescriptorResult successResult(MatchHistory matchHistory) {
        return new IdentifyByDescriptorResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getSuccess(),
                matchHistory.getFeatureId(),
                matchHistory.getSimilarity(),
                "",
                matchHistory.getTransactionUuid());
    }
}
