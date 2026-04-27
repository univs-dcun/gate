package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.application.input.VerifyByFaceIdInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import ai.univs.gate.shared.web.enums.CallerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByFaceIdRequestDTO(
        @Schema(description = SwaggerDescriptions.FACE_ID, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_FACE_ID")
        @Length(max = 100, message = "INVALID_FACE_ID_LENGTH")
        String faceId,

        @Schema(description = SwaggerDescriptions.MATCHING_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile matchingFaceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

        public VerifyByFaceIdInput toVerifyByFaceIdInput(Long accountId, String apiKey) {
                return new VerifyByFaceIdInput(
                        CallerType.API,
                        accountId,
                        apiKey,
                        faceId,
                        matchingFaceImage,
                        TransactionUtil.useOrCreate(transactionUuid));
        }
}
