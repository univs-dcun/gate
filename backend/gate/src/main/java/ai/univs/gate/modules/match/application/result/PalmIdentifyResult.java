package ai.univs.gate.modules.match.application.result;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PalmIdentifyResult(
        Long matchingHistoryId,
        Long palmMediaId,
        String palmId,
        Boolean success,
        BigDecimal similarity,
        String threshold,
        String failureType,
        String transactionUuid,
        LocalDateTime matchingTime
) {

    public static PalmIdentifyResult failResult(MatchHistory matchHistory, String failureType) {
        return new PalmIdentifyResult(
                matchHistory.getId(),
                null,
                null,
                false,
                matchHistory.getSimilarity(),
                null,
                failureType,
                matchHistory.getTransactionUuid(),
                matchHistory.getMatchTime());
    }

    public static PalmIdentifyResult successResult(MatchHistory matchHistory, PalmMedia palmMedia,
                                                   BigDecimal similarity, String threshold) {
        return new PalmIdentifyResult(
                matchHistory.getId(),
                palmMedia.getId(),
                palmMedia.getPalmId(),
                true,
                similarity,
                threshold,
                null,
                matchHistory.getTransactionUuid(),
                matchHistory.getMatchTime());
    }
}
