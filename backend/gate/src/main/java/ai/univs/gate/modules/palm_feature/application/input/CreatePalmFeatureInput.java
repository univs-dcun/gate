package ai.univs.gate.modules.palm_feature.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreatePalmFeatureInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String username,
        String transactionUuid,
        String externalKey
) {}
