package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.application.result.PalmIdentifyResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record PalmIdentifyResponseDTO(
        @Schema(description = "매칭 이력 ID")
        Long matchingHistoryId,

        @Schema(description = "팜 미디어 ID")
        Long palmMediaId,

        @Schema(description = "팜 ID (AI 서비스 발급)")
        String palmId,

        @Schema(description = "성공 여부")
        Boolean success,

        @Schema(description = "유사도 (%)")
        BigDecimal similarity,

        @Schema(description = "임계값")
        String threshold,

        @Schema(description = "실패 사유")
        String failureType,

        @Schema(description = "실패 사유 메시지")
        String failureReason,

        @Schema(description = "매칭 일시")
        LocalDateTime matchingTime,

        @Schema(description = "트랜잭션 UUID")
        String transactionUuid
) {

    public static PalmIdentifyResponseDTO from(PalmIdentifyResult result, String failureReason, String timezone) {
        return new PalmIdentifyResponseDTO(
                result.matchingHistoryId(),
                result.palmMediaId(),
                result.palmId(),
                result.success(),
                result.similarity(),
                result.threshold(),
                result.failureType(),
                failureReason,
                fromUtc(result.matchingTime(), timezone),
                result.transactionUuid());
    }
}
