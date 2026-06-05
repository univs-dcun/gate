package ai.univs.gate.modules.face_feature.application.result;

import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public record FaceFeatureResult(
        Long faceFeatureId,
        Long projectId,
        String featureId,
        String description,
        String username,
        String featureImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness
) {

    public static FaceFeatureResult from(FaceFeature faceFeature, String imagePrefix, boolean consentEnabled) {
        return new FaceFeatureResult(
                faceFeature.getId(),
                faceFeature.getProject().getId(),
                faceFeature.getFeatureId(),
                faceFeature.getDescription(),
                faceFeature.getUsername(),
                consentEnabled && StringUtils.hasText(faceFeature.getFeatureImagePath())
                        ? imagePrefix + faceFeature.getFeatureImagePath()
                        : "",
                faceFeature.getCreatedAt(),
                faceFeature.getTransactionUuid(),
                null);
    }

    public static FaceFeatureResult from(FaceFeature faceFeature, boolean livenessChecked, String imagePrefix, boolean consentEnabled) {
        return new FaceFeatureResult(
                faceFeature.getId(),
                faceFeature.getProject().getId(),
                faceFeature.getFeatureId(),
                faceFeature.getDescription(),
                faceFeature.getUsername(),
                consentEnabled && StringUtils.hasText(faceFeature.getFeatureImagePath())
                        ? imagePrefix + faceFeature.getFeatureImagePath()
                        : "",
                faceFeature.getCreatedAt(),
                faceFeature.getTransactionUuid(),
                livenessChecked);
    }
}
