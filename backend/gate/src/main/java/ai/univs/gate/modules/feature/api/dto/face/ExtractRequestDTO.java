package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.ExtractInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ExtractRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @NotNull(message = "REQUIRED_IMAGE_FILE")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile featureImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    // UG-278: 인자 순서를 (accountId, apiKey) 로 통일한다. 다른 to*Input 은 모두 이 순서다.
    public ExtractInput toExtractInput(Long accountId, String apiKey) {
        return new ExtractInput(
                accountId,
                apiKey,
                featureImage,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
