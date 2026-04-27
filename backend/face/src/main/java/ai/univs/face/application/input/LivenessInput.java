package ai.univs.face.application.input;

import org.springframework.web.multipart.MultipartFile;

public record LivenessInput(
        MultipartFile faceImage,
        String transactionUuid,
        String clientId
) {
}
