package ai.univs.gate.facade.sdk.application.input;

import org.springframework.web.multipart.MultipartFile;

public record VerifyByFaceIdAndTokenInput(
        String token,
        String faceId,
        MultipartFile matchingFaceImage
) {
}
