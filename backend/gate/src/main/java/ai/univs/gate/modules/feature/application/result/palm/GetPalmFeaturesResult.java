package ai.univs.gate.modules.feature.application.result.palm;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetPalmFeaturesResult(
        List<PalmFeatureResult> palmFeatures,
        CustomPageResult page
) {}
