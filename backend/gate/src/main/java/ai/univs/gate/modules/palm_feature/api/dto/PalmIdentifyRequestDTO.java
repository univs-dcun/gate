package ai.univs.gate.modules.palm_feature.api.dto;

import ai.univs.gate.modules.palm_feature.application.input.PalmIdentifyInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.ValidImageFile;
import ai.univs.gate.shared.web.enums.CallerType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record PalmIdentifyRequestDTO(
        @Schema(description = SwaggerDescriptions.PALM_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public PalmIdentifyInput toInput(Long accountId, String apiKey) {
        return new PalmIdentifyInput(
                CallerType.API,
                accountId,
                apiKey,
                featureImage,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString());
    }
}
