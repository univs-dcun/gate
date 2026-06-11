package ai.univs.gate.support.palm_feature;

import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;

public record CreatePalmFeatureServiceResult(
        PalmFeature palmFeature,
        boolean livenessChecked
) {}
