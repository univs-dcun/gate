package ai.univs.palm.api.dto;

import ai.univs.palm.application.input.LivenessInput;
import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import ai.univs.palm.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record LivenessRequestDTO(
        @Schema(description = SwaggerDescriptions.PALM_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile palmImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId
) {

    public LivenessInput toLivenessInput() {
        return new LivenessInput(
                palmImage,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM"
        );
    }
}
