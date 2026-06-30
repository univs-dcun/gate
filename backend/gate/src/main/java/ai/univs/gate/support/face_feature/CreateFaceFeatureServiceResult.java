package ai.univs.gate.support.face_feature;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

public record CreateFaceFeatureServiceResult(
        BiometricFeature biometricFeature,
        boolean livenessChecked
) {
}
