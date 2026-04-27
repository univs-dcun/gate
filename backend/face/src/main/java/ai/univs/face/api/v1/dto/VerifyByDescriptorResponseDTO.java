package ai.univs.face.api.v1.dto;

import ai.univs.face.application.result.VerifyByDescriptorResult;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyByDescriptorResponseDTO(
        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity,

        @Schema(description = SwaggerDescriptions.RESULT)
        boolean result
) {

    public static VerifyByDescriptorResponseDTO from(VerifyByDescriptorResult result) {
        return new VerifyByDescriptorResponseDTO(
                result.transactionUuid(),
                result.similarity(),
                result.result());
    }
}
