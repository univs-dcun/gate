package ai.univs.gate.facade.sdk.api.dto;

import ai.univs.gate.facade.sdk.application.input.CreatePalmMediaByTokenInput;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

public record CreatePalmMediaByTokenRequestDTO(
        @Schema(description = "팜 등록 QR 코드", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_CREATE_USER_CODE")
        @Length(min = 36, max = 36, message = "INVALID_CREATE_USER_CODE_LENGTH")
        String code,

        @Schema(description = "팜 이미지", requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile palmImage,

        @Schema(description = "팜 설명")
        @Length(max = 1000, message = "INVALID_USER_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = "트랜잭션 UUID")
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public CreatePalmMediaByTokenInput toInput() {
        return new CreatePalmMediaByTokenInput(
                code,
                palmImage,
                description,
                username,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
