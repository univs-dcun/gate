package ai.univs.face.application.input;

import org.springframework.web.multipart.MultipartFile;

public record VerifyByImageInput(
        MultipartFile faceImage,
        MultipartFile targetFaceImage,
        String transactionUuid,
        String clientId,
        boolean checkLiveness,
        boolean checkMultiFace
) {
}
