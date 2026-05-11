package ai.univs.gate.modules.user.api.dto;

import ai.univs.gate.modules.user.application.input.UpdateUserInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record UpdateUserRequestDTO(
        @Schema(description = SwaggerDescriptions.USER_ID, requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = Long.MIN_VALUE, message = "INVALID_USER_ID_VALUE")
        @Max(value = Long.MAX_VALUE, message = "INVALID_USER_ID_VALUE")
        Long userId,

        @Schema(description = SwaggerDescriptions.FACE_IMAGE, type = "string", format = "binary")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.FACE_ID)
        @Length(max = 255, message = "INVALID_FACE_ID_LENGTH")
        String faceId,

        @Schema(description = SwaggerDescriptions.USER_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public UpdateUserInput toUpdateUserInput(Long accountId, String apiKey) {
        return new UpdateUserInput(
                accountId,
                apiKey,
                userId,
                faceImage,
                faceId,
                description,
                "",
                transactionUuid);
    }
}
