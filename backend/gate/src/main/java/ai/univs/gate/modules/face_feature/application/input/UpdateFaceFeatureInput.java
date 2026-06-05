package ai.univs.gate.modules.face_feature.application.input;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import org.springframework.web.multipart.MultipartFile;

public record UpdateFaceFeatureInput(
        Long accountId,
        String apiKey,
        Long faceFeatureId,
        MultipartFile featureImage,
        String description,
        String username,
        String reason,
        String transactionUuid
) {

    public void validationFileExtension() {
        boolean isValidExtension =
                featureImage.getOriginalFilename().toLowerCase().endsWith(".jpg") ||
                featureImage.getOriginalFilename().toLowerCase().endsWith(".jpeg");

        if (!isValidExtension) {
            throw new CustomGateException(ErrorType.INVALID_FILE);
        }
    }

    public boolean hasImage() {
        return featureImage != null && !featureImage.isEmpty();
    }
}
