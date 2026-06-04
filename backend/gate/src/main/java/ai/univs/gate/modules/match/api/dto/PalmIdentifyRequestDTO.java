package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.application.input.PalmIdentifyInput;
import ai.univs.gate.shared.utils.ValidImageFile;
import ai.univs.gate.shared.web.enums.CallerType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record PalmIdentifyRequestDTO(
        @Schema(description = "팜 이미지", requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile palmImage,

        @Schema(description = "트랜잭션 UUID")
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public PalmIdentifyInput toInput(Long accountId, String apiKey) {
        return new PalmIdentifyInput(
                CallerType.API,
                accountId,
                apiKey,
                palmImage,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString());
    }
}
