package ai.univs.gate.modules.palm_media.api.dto;

import ai.univs.gate.modules.palm_media.application.input.UpdatePalmMediaInput;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record UpdatePalmMediaRequestDTO(
        @Schema(description = "새 팜 이미지 (선택)", type = "string", format = "binary")
        MultipartFile palmImage,

        @Schema(description = "팜 설명")
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description,

        @Schema(description = "사용자 이름")
        @Length(max = 255, message = "INVALID_USERNAME_LENGTH")
        String username,

        @Schema(description = "트랜잭션 UUID")
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public UpdatePalmMediaInput toInput(Long accountId, String apiKey, Long palmMediaId) {
        return new UpdatePalmMediaInput(
                accountId,
                apiKey,
                palmMediaId,
                palmImage,
                description,
                username,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString());
    }
}
