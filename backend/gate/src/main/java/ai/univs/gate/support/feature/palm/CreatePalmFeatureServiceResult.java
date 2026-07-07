package ai.univs.gate.support.feature.palm;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;

public record CreatePalmFeatureServiceResult(
        BiometricFeature biometricFeature,
        boolean livenessChecked
) {}
