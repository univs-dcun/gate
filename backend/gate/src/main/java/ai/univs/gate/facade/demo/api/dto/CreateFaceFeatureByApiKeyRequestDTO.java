package ai.univs.gate.facade.demo.api.dto;

import ai.univs.gate.facade.demo.application.input.CreateFaceFeatureByApiKeyInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record CreateFaceFeatureByApiKeyRequestDTO(
        @Schema(description = SwaggerDescriptions.API_KEY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_API_KEY")
        @Length(max = 36, message = "INVALID_API_KEY_LENGTH")
        String apiKey,

        @Schema(description = SwaggerDescriptions.FEATURE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.FACE_FEATURE_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = SwaggerDescriptions.USERNAME)
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

        public CreateFaceFeatureByApiKeyInput toCreateUserByApiKeyInput() {
                return new CreateFaceFeatureByApiKeyInput(
                        0L,
                        apiKey,
                        featureImage,
                        description,
                        username,
                        TransactionUtil.useOrCreate(transactionUuid));
        }
}
