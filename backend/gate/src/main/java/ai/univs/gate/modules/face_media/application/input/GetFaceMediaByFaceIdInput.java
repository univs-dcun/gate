package ai.univs.gate.modules.face_media.application.input;

public record GetFaceMediaByFaceIdInput(
        Long accountId,
        String apiKey,
        String faceId
) {
}
