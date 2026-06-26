package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.modules.face_feature.application.input.CreateFaceFeatureInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record CreateFaceFeatureRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @NotNull(message = "REQUIRED_IMAGE_FILE")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.FACE_FEATURE_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public CreateFaceFeatureInput toInput(Long accountId, String apiKey) {
        return new CreateFaceFeatureInput(
                accountId,
                apiKey,
                featureImage,
                description,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString());
    }
}
