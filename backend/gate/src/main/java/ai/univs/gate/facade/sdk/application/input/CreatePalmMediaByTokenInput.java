package ai.univs.gate.facade.sdk.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreatePalmMediaByTokenInput(
        String code,
        MultipartFile palmImage,
        String description,
        String username,
        String transactionUuid
) {
}
