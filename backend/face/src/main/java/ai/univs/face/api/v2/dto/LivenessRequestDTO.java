package ai.univs.face.api.v2.dto;

import ai.univs.face.application.input.LivenessInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import ai.univs.face.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record LivenessRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId
) {

    public LivenessInput toLivenessInput() {
        return new LivenessInput(
                faceImage,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM"
        );
    }
}
