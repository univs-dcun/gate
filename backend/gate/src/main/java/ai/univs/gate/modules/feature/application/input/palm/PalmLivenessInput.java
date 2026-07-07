package ai.univs.gate.modules.feature.application.input.palm;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record PalmLivenessInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String transactionUuid
) {}
