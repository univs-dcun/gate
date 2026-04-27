package ai.univs.face.api.v1.dto;

import ai.univs.face.application.result.VerifyByIdResult;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyByIdResponseDTO(
        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.FACE_ID)
        String faceId,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        String similarity,

        @Schema(description = SwaggerDescriptions.RESULT)
        boolean result
) {

    public static VerifyByIdResponseDTO from(VerifyByIdResult result) {
        return new VerifyByIdResponseDTO(
                result.transactionUuid(),
                result.faceId(),
                result.similarity(),
                result.result());
    }
}
