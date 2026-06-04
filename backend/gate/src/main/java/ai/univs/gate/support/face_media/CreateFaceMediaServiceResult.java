package ai.univs.gate.support.face_media;

import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;

public record CreateFaceMediaServiceResult(
        FaceMedia faceMedia,
        boolean livenessChecked
) {
}
