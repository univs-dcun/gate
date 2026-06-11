package ai.univs.gate.modules.palm_feature.application.result;

import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
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

    public static PalmFeatureResult from(PalmFeature palmFeature, String imagePrefix, boolean consentEnabled) {
        return new PalmFeatureResult(
                palmFeature.getId(),
                palmFeature.getProject().getId(),
                palmFeature.getFeatureId(),
                palmFeature.getDescription(),
                consentEnabled && StringUtils.hasText(palmFeature.getFeatureImagePath())
                        ? imagePrefix + palmFeature.getFeatureImagePath()
                        : "",
                palmFeature.getCreatedAt(),
                palmFeature.getTransactionUuid(),
                null);
    }

    public static PalmFeatureResult from(PalmFeature palmFeature, boolean livenessChecked, String imagePrefix, boolean consentEnabled) {
        return new PalmFeatureResult(
                palmFeature.getId(),
                palmFeature.getProject().getId(),
                palmFeature.getFeatureId(),
                palmFeature.getDescription(),
                consentEnabled && StringUtils.hasText(palmFeature.getFeatureImagePath())
                        ? imagePrefix + palmFeature.getFeatureImagePath()
                        : "",
                palmFeature.getCreatedAt(),
                palmFeature.getTransactionUuid(),
                livenessChecked);
    }
}
