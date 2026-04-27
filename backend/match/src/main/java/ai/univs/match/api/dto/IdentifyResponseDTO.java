package ai.univs.match.api.dto;

import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record IdentifyResponseDTO(
        @Schema(description = SwaggerDescriptions.FACE_ID)
        String faceId,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity
) {

        public static IdentifyResponseDTO from(IdentifyResult result) {
                return new IdentifyResponseDTO(result.faceId(), result.similarity());
        }
}
