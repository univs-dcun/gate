package ai.univs.gate.modules.face_media.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateFaceMediaInput(
        Long accountId,
        String apiKey,
        MultipartFile faceImage,
        String description,
        String username,
        String transactionUuid
) {
}
