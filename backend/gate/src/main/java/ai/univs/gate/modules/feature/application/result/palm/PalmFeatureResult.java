package ai.univs.gate.modules.feature.application.result.palm;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.support.file.ImageUrls;

import java.time.LocalDateTime;

public record PalmFeatureResult(
        Long palmFeatureId,
        Long projectId,
        String featureId,
        String description,
        String featureImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness,
        String externalKey
) {

    public static PalmFeatureResult from(BiometricFeature feature,
                                         ImageUrls imageUrls
    ) {
        return new PalmFeatureResult(
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

    public static PalmFeatureResult from(BiometricFeature feature,
                                         boolean livenessChecked,
                                         ImageUrls imageUrls
    ) {
        return new PalmFeatureResult(
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
