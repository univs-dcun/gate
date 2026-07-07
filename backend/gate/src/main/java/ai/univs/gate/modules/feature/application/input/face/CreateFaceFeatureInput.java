package ai.univs.gate.modules.feature.application.input.face;

import org.springframework.web.multipart.MultipartFile;

public record CreateFaceFeatureInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String transactionUuid
) {}
