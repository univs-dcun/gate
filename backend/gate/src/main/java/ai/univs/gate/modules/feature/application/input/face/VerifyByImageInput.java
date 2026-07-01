package ai.univs.gate.modules.feature.application.input.face;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record VerifyByImageInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        MultipartFile documentImage,
        MultipartFile matchingFeatureImage,
        String transactionUuid
) {}