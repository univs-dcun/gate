package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.modules.face_feature.application.input.ExtractInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

public record ExtractRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @NotNull(message = "REQUIRED_IMAGE_FILE")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public ExtractInput toExtractInput(String apiKey, Long accountId) {
        return new ExtractInput(
                apiKey,
                accountId,
                featureImage,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
