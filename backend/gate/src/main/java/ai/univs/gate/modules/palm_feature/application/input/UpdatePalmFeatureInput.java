package ai.univs.gate.modules.palm_feature.application.input;

import org.springframework.web.multipart.MultipartFile;

public record UpdatePalmFeatureInput(
        Long accountId,
        String apiKey,
        Long palmFeatureId,
        MultipartFile featureImage,
        String description,
        String username,
        String transactionUuid
) {}
