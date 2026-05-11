package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.application.input.VerifyByImageInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import ai.univs.gate.shared.web.enums.CallerType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByImageRequestDTO(
        @Schema(description = SwaggerDescriptions.TARGET_MATCHING_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile targetMatchingFaceImage,

        @Schema(description = SwaggerDescriptions.MATCHING_FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile matchingFaceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

        public VerifyByImageInput toVerifyByImageInput(Long accountId, String timezone) {
                return new VerifyByImageInput(
                        CallerType.API,
                        accountId,
                        timezone,
                        targetMatchingFaceImage,
                        matchingFaceImage,
                        TransactionUtil.useOrCreate(transactionUuid));
        }
}
