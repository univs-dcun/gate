package ai.univs.gate.modules.user.application.input;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import org.springframework.web.multipart.MultipartFile;

public record UpdateUserInput(
        Long accountId,
        String apiKey,
        Long userId,
        MultipartFile faceImage,
        String faceId,
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
