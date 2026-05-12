package ai.univs.gate.facade.sdk.api.dto;

import ai.univs.gate.facade.sdk.application.input.CreateUserByTokenInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record CreateUserByTokenRequestDTO(
        @Schema(description = SwaggerDescriptions.CREATE_USER_CODE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_CREATE_USER_CODE")
        @Length(min = 36, max = 36, message = "INVALID_CREATE_USER_CODE_LENGTH")
        String code,

        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.USER_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String userDescription,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public CreateUserByTokenInput toCreateUserByTokenInput() {
        return new CreateUserByTokenInput(
                code,
                faceImage,
                userDescription,
                username,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
