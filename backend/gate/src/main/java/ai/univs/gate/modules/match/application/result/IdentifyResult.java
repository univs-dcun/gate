package ai.univs.gate.modules.match.application.result;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IdentifyResult(
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
        String faceImagePath,
        String matchingFaceImagePath,
        String failureType,
        String transactionUuid
) {

    public static IdentifyResult failResult(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new IdentifyResult(
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
                "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchFaceImagePath())
                        ? prefixImagePath + matchHistory.getMatchFaceImagePath()
                        : "",
                matchHistory.getFailureType(),
                matchHistory.getTransactionUuid());
    }

    public static IdentifyResult successResult(MatchHistory matchHistory, String prefixImagePath, boolean consentEnabled) {
        return new IdentifyResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getCheckLiveness(),
                matchHistory.getSuccess(),
                matchHistory.getFaceId(),
                matchHistory.getUserId(),
                matchHistory.getUserDescription(),
                matchHistory.getSimilarity(),
                consentEnabled && StringUtils.hasText(matchHistory.getFaceImagePath())
                        ? prefixImagePath + matchHistory.getFaceImagePath()
                        : "",
                consentEnabled && StringUtils.hasText(matchHistory.getMatchFaceImagePath())
                        ? prefixImagePath + matchHistory.getMatchFaceImagePath()
                        : "",
                "",
                matchHistory.getTransactionUuid());
    }
}
