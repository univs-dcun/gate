package ai.univs.palm.application.input;

import org.springframework.web.multipart.MultipartFile;

public record ExtractInput(
        MultipartFile palmImage,
        String transactionUuid,
        String clientId
) {
}
