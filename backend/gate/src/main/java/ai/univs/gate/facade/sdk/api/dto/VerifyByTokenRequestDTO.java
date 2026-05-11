package ai.univs.gate.facade.sdk.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByTokenRequestDTO(
        @Schema(description = SwaggerDescriptions.VERIFY_CODE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_VERIFY_CODE")
        @Length(min = 36, max = 36, message = "INVALID_VERIFY_CODE_LENGTH")
        String code,

        @Schema(description = SwaggerDescriptions.MATCHING_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile matchingFaceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {
}
