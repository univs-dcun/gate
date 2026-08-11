package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 특징점 기반 1:N 후보 목록 매칭 결과 (UG-314).
 *
 * <p>{@link IdentifyByDescriptorResult} 와 달리 단일 {@code featureId}/{@code similarity} 대신
 * {@code candidates} 목록을 담는다. 나머지 필드가 빠진 사유는 그쪽과 같다.
 *
 * <p><b>이력은 요청 하나에 한 행이다</b> (패턴 A). {@code matchingHistoryId} 는 그 한 행을
 * 가리키고, 그 행의 대표값은 최상위 후보다. 후보 목록 전체는 여기에만 있고 저장되지 않는다.
 */
public record IdentifyCandidatesByDescriptorResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean success,
        List<Candidate> candidates,
        BigDecimal threshold,
        String failureType,
        String transactionUuid
) {

    /** 유사도는 백분율이다 (0.00 ~ 100.00) — 기존 응답의 similarity 와 같은 스케일이다. */
    public record Candidate(
            String featureId,
            String userDescription,
            BigDecimal similarity
    ) {
    }

    public static IdentifyCandidatesByDescriptorResult failResult(
            MatchHistory matchHistory, BigDecimal thresholdPercent) {
        return new IdentifyCandidatesByDescriptorResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getSuccess(),
                List.of(),
                thresholdPercent,
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid());
    }

    public static IdentifyCandidatesByDescriptorResult successResult(
            MatchHistory matchHistory, BigDecimal thresholdPercent, List<Candidate> candidates) {
        return new IdentifyCandidatesByDescriptorResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getSuccess(),
                candidates,
                thresholdPercent,
                "",
                matchHistory.getTransactionUuid());
    }
}
