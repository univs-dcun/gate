package ai.univs.face.api.v1.dto;

import ai.univs.face.application.input.VerifyByImageInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import ai.univs.face.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record VerifyByImageRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.TARGET_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile targetFaceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.CHECK_MULTI_FACE)
        Boolean checkMultiFace
) {

    public VerifyByImageInput toVerifyByImageInput() {
        return new VerifyByImageInput(
                faceImage,
                targetFaceImage,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM",
                checkLiveness != null ? checkLiveness : true,
                checkMultiFace != null ? checkMultiFace : true);
    }
}
