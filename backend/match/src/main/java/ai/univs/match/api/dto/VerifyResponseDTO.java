package ai.univs.match.api.dto;

import ai.univs.match.application.result.VerifyResult;
import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyResponseDTO(
        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity
) {

        public static VerifyResponseDTO from(VerifyResult result) {
                return new VerifyResponseDTO(result.similarity());
        }
}
