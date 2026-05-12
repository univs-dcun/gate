package ai.univs.gate.facade.demo.api.dto;

import ai.univs.gate.facade.demo.application.input.CreateUserByApiKeyInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record CreateUserByApiKeyRequestDTO(
        @Schema(description = SwaggerDescriptions.API_KEY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_API_KEY")
        @Length(max = 36, message = "INVALID_API_KEY_LENGTH")
        String apiKey,

        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.USER_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String userDescription,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

        public CreateUserByApiKeyInput toCreateUserByApiKeyInput() {
                return new CreateUserByApiKeyInput(
                        0L,
                        apiKey,
                        faceImage,
                        userDescription,
                        username,
                        TransactionUtil.useOrCreate(transactionUuid));
        }
}
