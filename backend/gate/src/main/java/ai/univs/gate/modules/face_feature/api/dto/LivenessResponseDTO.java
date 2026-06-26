package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.modules.face_feature.application.result.LivenessResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record LivenessResponseDTO(
        @Schema(description = SwaggerDescriptions.LIVENESS_SUCCESS)
        boolean success,

        @Schema(description = SwaggerDescriptions.LIVENESS_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CONSENT_ENABLED)
        Boolean consentSnapshot
) {

    public static LivenessResponseDTO from(LivenessResult result, String failureReason) {
        return new LivenessResponseDTO(
                result.success(),
                failureReason,
                result.transactionUuid(),
                result.consentSnapshot());
    }
}
