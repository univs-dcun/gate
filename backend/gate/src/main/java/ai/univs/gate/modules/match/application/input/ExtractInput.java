package ai.univs.gate.modules.match.application.input;

import org.springframework.web.multipart.MultipartFile;

public record ExtractInput(
        String apiKey,
        Long accountId,
        MultipartFile faceImage,
        String transactionUuid
) {
}
