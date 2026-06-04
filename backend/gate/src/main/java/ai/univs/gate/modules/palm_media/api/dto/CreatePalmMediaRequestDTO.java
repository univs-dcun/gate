package ai.univs.gate.modules.palm_media.api.dto;

import ai.univs.gate.modules.palm_media.application.input.CreatePalmMediaInput;
import ai.univs.gate.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record CreatePalmMediaRequestDTO(
        @Schema(description = "팜 이미지", requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile palmImage,

        @Schema(description = "팜 설명")
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = "트랜잭션 UUID")
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid,

        @Schema(description = "외부 연결 키 (face ↔ palm 연결용)")
        @Length(max = 255, message = "INVALID_EXTERNAL_KEY_LENGTH")
        String externalKey
) {

    public CreatePalmMediaInput toInput(Long accountId, String apiKey) {
        return new CreatePalmMediaInput(
                accountId,
                apiKey,
                palmImage,
                description,
                username,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                externalKey);
    }
}
