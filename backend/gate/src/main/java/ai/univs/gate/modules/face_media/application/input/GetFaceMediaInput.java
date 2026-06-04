package ai.univs.gate.modules.face_media.application.input;

public record GetFaceMediaInput(
        Long accountId,
        String apiKey,
        Long faceMediaId
) {
}
