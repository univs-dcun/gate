package ai.univs.gate.modules.match.application.result;

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
        String faceId,
        Long userId,
        String userDescription,
        BigDecimal similarity,
        String matchingFaceId,
        String matchingFaceImagePath,
        String failureType,
        String transactionUuid
) {

    public static VerifyByFaceIdResult failResult(MatchHistory matchHistory, String prefixImagePath) {
        return new VerifyByFaceIdResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
                null,
                "",
                matchHistory.getSimilarity(),
                matchHistory.getMatchFaceId(),
                StringUtils.hasText(matchHistory.getMatchFaceImagePath())
                        ? prefixImagePath + matchHistory.getMatchFaceImagePath()
                        : "",
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid());
    }

    public static VerifyByFaceIdResult successResult(MatchHistory history, String prefixImagePath) {
        return new VerifyByFaceIdResult(
                history.getId(),
                history.getProject().getId(),
                history.getMatchType(),
                history.getMatchTime(),
                history.getCheckLiveness(),
                history.getSuccess(),
                history.getFaceId(),
                history.getUserId(),
                history.getUserDescription(),
                history.getSimilarity(),
                history.getMatchFaceId(),
                StringUtils.hasText(history.getMatchFaceImagePath())
                        ? prefixImagePath + history.getMatchFaceImagePath() :
                        "",
                "",
                history.getTransactionUuid());
    }
}
