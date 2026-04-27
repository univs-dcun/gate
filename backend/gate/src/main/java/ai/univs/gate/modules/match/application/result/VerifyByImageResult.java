package ai.univs.gate.modules.match.application.result;

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
        String faceId,
        Long userId,
        BigDecimal similarity,
        String matchingFaceImagePath,
        String targetMatchingFaceImagePath,
        String failureType,
        String transactionUuid
) {

    public static VerifyByImageResult failResult(MatchHistory matchHistory, String prefixImagePath) {
        return new VerifyByImageResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                "",
                null,
                matchHistory.getSimilarity(),
                StringUtils.hasText(matchHistory.getFaceImagePath())
                        ? prefixImagePath + matchHistory.getFaceImagePath() :
                        "",
                StringUtils.hasText(matchHistory.getMatchFaceImagePath())
                        ? prefixImagePath + matchHistory.getMatchFaceImagePath() :
                        "",
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid());
    }

    public static VerifyByImageResult successResult(MatchHistory matchHistory, String prefixImagePath) {
        return new VerifyByImageResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFaceId(),
                matchHistory.getUserId(),
                matchHistory.getSimilarity(),
                StringUtils.hasText(matchHistory.getFaceImagePath())
                        ? prefixImagePath + matchHistory.getFaceImagePath() :
                        "",
                StringUtils.hasText(matchHistory.getMatchFaceImagePath())
                        ? prefixImagePath + matchHistory.getMatchFaceImagePath() :
                        "",
                "",
                matchHistory.getTransactionUuid());
    }
}
