package ai.univs.gate.modules.feature.application.input.face;

import org.springframework.web.multipart.MultipartFile;

public record ExtractInput(
        String apiKey,
        Long accountId,
        MultipartFile featureImage,
        String transactionUuid
) {}