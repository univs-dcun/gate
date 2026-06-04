package ai.univs.gate.modules.face_media.application.input;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import org.springframework.web.multipart.MultipartFile;

public record UpdateFaceMediaInput(
        Long accountId,
        String apiKey,
        Long faceMediaId,
        MultipartFile faceImage,
        String description,
        String username,
        String reason,
        String transactionUuid
) {

    public void validationFileExtension() {
        boolean isValidExtension =
                faceImage.getOriginalFilename().toLowerCase().endsWith(".jpg") ||
                faceImage.getOriginalFilename().toLowerCase().endsWith(".jpeg");

        if (!isValidExtension) {
            throw new CustomGateException(ErrorType.INVALID_FILE);
        }
    }

    public boolean hasImage() {
        return faceImage != null && !faceImage.isEmpty();
    }
}
