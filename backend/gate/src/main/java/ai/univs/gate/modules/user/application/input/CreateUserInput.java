package ai.univs.gate.modules.user.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateUserInput(
        Long accountId,
        String apiKey,
        MultipartFile faceImage,
        String description,
        String transactionUuid
) {
}
