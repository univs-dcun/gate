package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.support.file.ImageUrls;
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
        Boolean checkLiveness,
        String externalKey
) {

    public static FaceFeatureResult from(BiometricFeature feature,
                                         ImageUrls imageUrls
    ) {
        return new FaceFeatureResult(
                feature.getId(),
                feature.getProject().getId(),
                feature.getFeatureId(),
                feature.getDescription(),
                imageUrls.of(feature.getFeatureImagePath()),
                feature.getCreatedAt(),
                feature.getTransactionUuid(),
                null,
                feature.getExternalKey());
    }

    public static FaceFeatureResult from(BiometricFeature feature,
                                         boolean livenessChecked,
                                         ImageUrls imageUrls
    ) {
        return new FaceFeatureResult(
                feature.getId(),
                feature.getProject().getId(),
                feature.getFeatureId(),
                feature.getDescription(),
                imageUrls.of(feature.getFeatureImagePath()),
                feature.getCreatedAt(),
                feature.getTransactionUuid(),
                livenessChecked,
                feature.getExternalKey());
    }
}
