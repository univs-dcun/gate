package ai.univs.gate.modules.feature.api.dto.palm;

import ai.univs.gate.modules.feature.application.input.palm.UpdatePalmFeatureInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record UpdatePalmFeatureRequestDTO(
        @Schema(description = SwaggerDescriptions.PALM_IMAGE_OPTIONAL, type = "string", format = "binary")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.PALM_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public UpdatePalmFeatureInput toInput(Long accountId, String apiKey, Long palmFeatureId) {
        return new UpdatePalmFeatureInput(
                accountId,
                apiKey,
                palmFeatureId,
                featureImage,
                description,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString());
    }
}
