package ai.univs.gate.modules.match.application.input;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByImageInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        MultipartFile targetMatchingFaceImage,
        MultipartFile matchingFaceImage,
        String transactionUuid
) {
}
