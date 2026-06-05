package ai.univs.gate.support.face_feature;

import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;

public record CreateFaceFeatureServiceResult(
        FaceFeature faceFeature,
        boolean livenessChecked
) {
}
