package ai.univs.gate.modules.face_feature.application.input;

public record GetFaceFeatureInput(
        Long accountId,
        String apiKey,
        Long faceFeatureId
) {
}
