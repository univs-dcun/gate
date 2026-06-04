package ai.univs.gate.modules.palm_media.application.input;

import org.springframework.web.multipart.MultipartFile;

public record UpdatePalmMediaInput(
        Long accountId,
        String apiKey,
        Long palmMediaId,
        MultipartFile palmImage,
        String description,
        String username,
        String transactionUuid
) {}
