package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.result.face.FaceFeatureByDescriptorResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

/**
 * descriptor 기반 등록 응답 (UG-279).
 *
 * <p>{@link FaceFeatureResponseDTO} 와 분리한 이유는
 * {@link FaceFeatureByDescriptorResult} 주석 참고.
 */
public record FaceFeatureByDescriptorResponseDTO(
        @Schema(description = SwaggerDescriptions.FACE_FEATURE_ID)
        Long faceFeatureId,

        @Schema(description = SwaggerDescriptions.FEATURE_ID)
        String featureId,

        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public static FaceFeatureByDescriptorResponseDTO from(FaceFeatureByDescriptorResult result, String timezone) {
        return new FaceFeatureByDescriptorResponseDTO(
                result.faceFeatureId(),
                result.featureId(),
                fromUtc(result.createdAt(), timezone),
                result.transactionUuid());
    }
}
