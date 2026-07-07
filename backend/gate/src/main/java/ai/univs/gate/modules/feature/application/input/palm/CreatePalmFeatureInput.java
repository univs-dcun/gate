package ai.univs.gate.modules.feature.application.input.palm;

import org.springframework.web.multipart.MultipartFile;

public record CreatePalmFeatureInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String transactionUuid,
        String externalKey
) {}
