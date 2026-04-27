package ai.univs.gate.modules.match.application.input;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByFaceIdInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        String faceId,
        MultipartFile matchingFaceImage,
        String transactionUuid
) {
}
