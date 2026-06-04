package ai.univs.gate.modules.palm_media.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreatePalmMediaInput(
        Long accountId,
        String apiKey,
        MultipartFile palmImage,
        String description,
        String username,
        String transactionUuid,
        String externalKey
) {}
