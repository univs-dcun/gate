package ai.univs.gate.modules.palm_feature.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetPalmFeaturesResult(
        List<PalmFeatureResult> palmFeatures,
        CustomPageResult page
) {}
