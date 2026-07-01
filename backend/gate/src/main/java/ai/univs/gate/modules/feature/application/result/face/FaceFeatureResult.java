package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.shared.utils.ImagePathUtil;
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

    public static FaceFeatureResult from(BiometricFeature feature,
                                         String prefixImagePath,
                                         boolean consentEnabled
    ) {
        return new FaceFeatureResult(
                feature.getId(),
                feature.getProject().getId(),
                feature.getFeatureId(),
                feature.getDescription(),
                ImagePathUtil.get(consentEnabled, prefixImagePath, feature.getFeatureImagePath()),
                feature.getCreatedAt(),
                feature.getTransactionUuid(),
                null);
    }

    public static FaceFeatureResult from(BiometricFeature feature,
                                         boolean livenessChecked,
                                         String prefixImagePath,
                                         boolean consentEnabled
    ) {
        return new FaceFeatureResult(
                feature.getId(),
                feature.getProject().getId(),
                feature.getFeatureId(),
                feature.getDescription(),
                ImagePathUtil.get(consentEnabled, prefixImagePath, feature.getFeatureImagePath()),
                feature.getCreatedAt(),
                feature.getTransactionUuid(),
                livenessChecked);
    }
}
