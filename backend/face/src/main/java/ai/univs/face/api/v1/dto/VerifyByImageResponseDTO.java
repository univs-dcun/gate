package ai.univs.face.api.v1.dto;

import ai.univs.face.application.result.VerifyByImageResult;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyByImageResponseDTO(
        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity,

        @Schema(description = SwaggerDescriptions.RESULT)
        boolean result
) {

    public static VerifyByImageResponseDTO from(VerifyByImageResult result) {
        return new VerifyByImageResponseDTO(
                result.transactionUuid(),
                result.similarity(),
                result.result());
    }
}
