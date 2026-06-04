package ai.univs.gate.modules.face_media.application.input;

public record DeleteFaceMediaInput(
        Long accountId,
        String apiKey,
        Long faceMediaId
) {
}
