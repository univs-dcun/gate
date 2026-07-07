package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.UpdateFaceFeatureInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record UpdateFaceFeatureRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, type = "string", format = "binary")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.FACE_FEATURE_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String description,

        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public UpdateFaceFeatureInput toInput(Long accountId, String apiKey, Long faceFeatureId) {
        return new UpdateFaceFeatureInput(
                accountId,
                apiKey,
                faceFeatureId,
                featureImage,
                description,
                "",
                transactionUuid);
    }
}
