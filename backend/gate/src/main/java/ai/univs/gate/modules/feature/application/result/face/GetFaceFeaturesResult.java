package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetFaceFeaturesResult(
        List<FaceFeatureResult> faceFeatures,
        CustomPageResult page
) {
}
