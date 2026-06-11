package ai.univs.gate.modules.face_feature.application.input;

import org.springframework.web.multipart.MultipartFile;

public record ExtractInput(
        String apiKey,
        Long accountId,
        MultipartFile featureImage,
        String transactionUuid
) {
}
