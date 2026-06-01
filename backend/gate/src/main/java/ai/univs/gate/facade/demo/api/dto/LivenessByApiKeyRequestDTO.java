package ai.univs.gate.facade.demo.api.dto;

import ai.univs.gate.modules.match.application.input.LivenessInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import ai.univs.gate.shared.web.enums.CallerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record LivenessByApiKeyRequestDTO(
        @Schema(description = SwaggerDescriptions.API_KEY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_API_KEY")
        @Length(max = 36, message = "INVALID_API_KEY_LENGTH")
        String apiKey,

        @Schema(description = SwaggerDescriptions.MATCHING_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile matchingFaceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

        public LivenessInput toLivenessInput() {
                return new LivenessInput(
                        CallerType.DEMO,
                        0L,
                        apiKey,
                        matchingFaceImage,
                        TransactionUtil.useOrCreate(transactionUuid));
        }
}
