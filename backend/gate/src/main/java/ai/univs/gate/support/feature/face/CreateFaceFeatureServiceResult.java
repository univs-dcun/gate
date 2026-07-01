package ai.univs.gate.support.feature.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

public record CreateFaceFeatureServiceResult(
        BiometricFeature biometricFeature,
        boolean livenessChecked
) {
}
