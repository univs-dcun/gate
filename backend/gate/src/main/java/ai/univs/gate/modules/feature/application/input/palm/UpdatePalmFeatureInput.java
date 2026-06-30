package ai.univs.gate.modules.feature.application.input.palm;

import org.springframework.web.multipart.MultipartFile;

public record UpdatePalmFeatureInput(
        Long accountId,
        String apiKey,
        Long palmFeatureId,
        MultipartFile featureImage,
        String description,
        String transactionUuid
) {}
