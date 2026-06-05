package ai.univs.gate.modules.face_feature.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateFaceFeatureInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String username,
        String transactionUuid
) {
}
