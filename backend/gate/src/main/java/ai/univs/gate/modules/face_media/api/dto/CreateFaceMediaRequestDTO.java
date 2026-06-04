package ai.univs.gate.modules.face_media.api.dto;

import ai.univs.gate.modules.face_media.application.input.CreateFaceMediaInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record CreateFaceMediaRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.USER_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String userDescription,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public CreateFaceMediaInput toInput(Long accountId, String apiKey) {
        return new CreateFaceMediaInput(
                accountId,
                apiKey,
                faceImage,
                userDescription,
                username,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString());
    }
}
