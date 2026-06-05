package ai.univs.gate.facade.demo.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateFaceFeatureByApiKeyInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String username,
        String transactionUuid
) {
}
