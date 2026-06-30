package ai.univs.gate.modules.feature.application.input.face;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByFaceIdInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        String faceId,
        MultipartFile matchingFeatureImage,
        String transactionUuid
) {
}
