package ai.univs.gate.modules.match.application.input;

import ai.univs.gate.shared.web.enums.CallerType;
import org.springframework.web.multipart.MultipartFile;

public record PalmLivenessInput(
        CallerType callerType,
        Long accountId,
        String apiKey,
        MultipartFile palmImage,
        String transactionUuid
) {}
