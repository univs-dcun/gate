package ai.univs.gate.facade.sdk.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateUserByTokenInput(
        String code,
        MultipartFile faceImage,
        String userDescription,
        String transactionUuid
) {
}
