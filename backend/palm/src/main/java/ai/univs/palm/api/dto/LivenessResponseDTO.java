package ai.univs.palm.api.dto;

import ai.univs.palm.application.result.LivenessResult;
import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LivenessResponseDTO(
        @Schema(description = SwaggerDescriptions.LIVENESS_SUCCESS_BOOLEAN)
        boolean success,

        @Schema(description = SwaggerDescriptions.LIVENESS_SCORE)
        double score,

        @Schema(description = SwaggerDescriptions.LIVENESS_THRESHOLD)
        double threshold,

        @Schema(description = SwaggerDescriptions.LIVENESS_MESSAGE)
        String message
) {

    public static LivenessResponseDTO from(LivenessResult result) {
        return new LivenessResponseDTO(
                result.success(),
                result.score(),
                result.threshold(),
                result.message());
    }
}
