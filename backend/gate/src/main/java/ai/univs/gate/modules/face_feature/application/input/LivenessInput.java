package ai.univs.gate.modules.face_feature.application.input;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record LivenessInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        MultipartFile matchingFeatureImage,
        String transactionUuid
) {
}
