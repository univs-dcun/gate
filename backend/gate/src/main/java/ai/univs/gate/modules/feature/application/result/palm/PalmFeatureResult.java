package ai.univs.gate.modules.feature.application.result.palm;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public record PalmFeatureResult(
        Long palmFeatureId,
        Long projectId,
        String featureId,
        String description,
        String featureImagePath,
        LocalDateTime createdAt,
        String transactionUuid,
        Boolean checkLiveness
) {

    public static PalmFeatureResult from(BiometricFeature feature, String imagePrefix, boolean consentEnabled) {
        return new PalmFeatureResult(
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

    public static PalmFeatureResult from(BiometricFeature feature, boolean livenessChecked, String imagePrefix, boolean consentEnabled) {
        return new PalmFeatureResult(
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
