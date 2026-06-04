package ai.univs.gate.modules.face_media.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetFaceMediasResult(
        List<FaceMediaResult> faceMedias,
        CustomPageResult page
) {
}
