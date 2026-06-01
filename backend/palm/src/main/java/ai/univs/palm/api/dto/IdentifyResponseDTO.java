package ai.univs.palm.api.dto;

import ai.univs.palm.application.result.IdentifyResult;
import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record IdentifyResponseDTO(
        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.PALM_ID)
        String palmId,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity,

        @Schema(description = SwaggerDescriptions.LIVENESS_THRESHOLD)
        String threshold,

        @Schema(description = SwaggerDescriptions.RESULT)
        boolean result
) {

    public static IdentifyResponseDTO from(IdentifyResult result) {
        return new IdentifyResponseDTO(
                result.transactionUuid(),
                result.palmId(),
                result.similarity(),
                result.threshold(),
                result.result());
    }
}
