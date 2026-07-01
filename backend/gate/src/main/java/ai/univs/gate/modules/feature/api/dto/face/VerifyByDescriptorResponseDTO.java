package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.result.face.VerifyByDescriptorResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyByDescriptorResponseDTO(
        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity,

        @Schema(description = SwaggerDescriptions.MATCHING_RESULT_TYPE)
        boolean result
) {

    public static VerifyByDescriptorResponseDTO from(VerifyByDescriptorResult result) {
        return new VerifyByDescriptorResponseDTO(
                result.transactionUuid(),
                result.similarity(),
                result.result());
    }
}
