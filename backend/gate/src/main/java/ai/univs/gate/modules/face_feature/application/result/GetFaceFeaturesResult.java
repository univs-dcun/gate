package ai.univs.gate.modules.face_feature.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetFaceFeaturesResult(
        List<FaceFeatureResult> faceFeatures,
        CustomPageResult page
) {
}
