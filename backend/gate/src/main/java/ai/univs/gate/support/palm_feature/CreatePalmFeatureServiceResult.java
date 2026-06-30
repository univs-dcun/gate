package ai.univs.gate.support.palm_feature;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

public record CreatePalmFeatureServiceResult(
        BiometricFeature biometricFeature,
        boolean livenessChecked
) {}
