package ai.univs.gate.facade.demo.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateUserByApiKeyInput(
        Long accountId,
        String apiKey,
        MultipartFile faceImage,
        String userDescription,
        String username,
        String transactionUuid
) {
}
