package ai.univs.gate.modules.face_feature.application.result;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public record FaceFeatureResult(
        Long faceFeatureId,
        Long projectId,
        String featureId,
        String description,
        String featureImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness
) {

    public static FaceFeatureResult from(BiometricFeature feature, String imagePrefix, boolean consentEnabled) {
        return new FaceFeatureResult(
                feature.getId(),
                feature.getProject().getId(),
                feature.getFeatureId(),
                feature.getDescription(),
                consentEnabled && StringUtils.hasText(feature.getFeatureImagePath())
                        ? imagePrefix + feature.getFeatureImagePath()
                        : "",
                feature.getCreatedAt(),
                feature.getTransactionUuid(),
                null);
    }

    public static FaceFeatureResult from(BiometricFeature feature, boolean livenessChecked, String imagePrefix, boolean consentEnabled) {
        return new FaceFeatureResult(
                feature.getId(),
                feature.getProject().getId(),
                feature.getFeatureId(),
                feature.getDescription(),
                consentEnabled && StringUtils.hasText(feature.getFeatureImagePath())
                        ? imagePrefix + feature.getFeatureImagePath()
                        : "",
                feature.getCreatedAt(),
                feature.getTransactionUuid(),
                livenessChecked);
    }
}
