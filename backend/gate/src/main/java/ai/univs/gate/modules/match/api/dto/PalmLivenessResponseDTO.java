package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.application.result.PalmLivenessResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PalmLivenessResponseDTO(
        @Schema(description = "라이브니스 성공 여부")
        boolean success,

        @Schema(description = "라이브니스 점수")
        double score,

        @Schema(description = "임계값")
        double threshold,

        @Schema(description = "실패 사유 메시지")
        String failureReason,

        @Schema(description = "트랜잭션 UUID")
        String transactionUuid
) {

    public static PalmLivenessResponseDTO from(PalmLivenessResult result, String failureReason) {
        return new PalmLivenessResponseDTO(
                result.success(),
                result.score(),
                result.threshold(),
                failureReason,
                result.transactionUuid());
    }
}
