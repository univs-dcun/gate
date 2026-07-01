package ai.univs.gate.modules.feature.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateFeatureInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String transactionUuid
) {
}
