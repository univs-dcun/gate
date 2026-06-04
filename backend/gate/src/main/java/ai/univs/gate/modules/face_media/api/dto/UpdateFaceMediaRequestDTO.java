package ai.univs.gate.modules.face_media.api.dto;

import ai.univs.gate.modules.face_media.application.input.UpdateFaceMediaInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record UpdateFaceMediaRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, type = "string", format = "binary")
        MultipartFile faceImage,

        @Schema(description = "페이스 미디어 설명")
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public UpdateFaceMediaInput toInput(Long accountId, String apiKey, Long faceMediaId) {
        return new UpdateFaceMediaInput(
                accountId,
                apiKey,
                faceMediaId,
                faceImage,
                description,
                username,
                "",
                transactionUuid);
    }
}
