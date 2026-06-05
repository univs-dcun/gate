package ai.univs.gate.modules.palm_feature.api.dto;

import ai.univs.gate.modules.palm_feature.application.result.PalmLivenessResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record PalmLivenessResponseDTO(
        @Schema(description = SwaggerDescriptions.LIVENESS_SUCCESS)
        boolean success,

        @Schema(description = SwaggerDescriptions.LIVENESS_SCORE)
        double score,

        @Schema(description = SwaggerDescriptions.THRESHOLD)
        double threshold,

        @Schema(description = SwaggerDescriptions.LIVENESS_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
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
