package ai.univs.palm.application.input;

import org.springframework.web.multipart.MultipartFile;

public record LivenessInput(
        MultipartFile palmImage,
        String transactionUuid,
        String clientId
) {
}
