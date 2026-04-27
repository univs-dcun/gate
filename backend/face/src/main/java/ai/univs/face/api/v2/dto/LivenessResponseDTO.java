package ai.univs.face.api.v2.dto;

import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record LivenessResponseDTO(
        @Schema(description = SwaggerDescriptions.LIVENESS_SUCCESS_BOOLEAN)
        boolean success,

        @Schema(description = SwaggerDescriptions.LIVENESS_SCORE)
        String probability,

        @Schema(description = SwaggerDescriptions.LIVENESS_SUCCESS_NUMBERING)
        int prdioction,

        @Schema(description = SwaggerDescriptions.LIVENESS_SUCCESS_TEXT)
        String prdioctionDesc,

        @Schema(description = SwaggerDescriptions.IMAGE_QUALITY)
        String quality,

        @Schema(description = SwaggerDescriptions.LIVENESS_THRESHOLD)
        String threshold
) {

    public static LivenessResponseDTO from(LivenessResult result) {
        return new LivenessResponseDTO(
                result.success(),
                result.probability(),
                result.prdioction(),
                result.prdioctionDesc(),
                result.quality(),
                result.threshold());
    }
}
