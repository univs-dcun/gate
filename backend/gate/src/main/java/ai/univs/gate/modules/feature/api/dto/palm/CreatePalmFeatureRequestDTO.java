package ai.univs.gate.modules.feature.api.dto.palm;

import ai.univs.gate.modules.feature.application.input.palm.CreatePalmFeatureInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record CreatePalmFeatureRequestDTO(
        @Schema(description = SwaggerDescriptions.PALM_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @NotNull(message = "REQUIRED_IMAGE_FILE")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.PALM_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description,

        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.EXTERNAL_KEY)
        @Length(max = 255, message = "INVALID_EXTERNAL_KEY_LENGTH")
        String externalKey
) {

    public CreatePalmFeatureInput toInput(Long accountId, String apiKey) {
        return new CreatePalmFeatureInput(
                accountId,
                apiKey,
                featureImage,
                description,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                externalKey);
    }
}
