package ai.univs.gate.modules.face_feature.application.input;

public record DeleteFaceFeatureInput(
        Long accountId,
        String apiKey,
        Long faceFeatureId
) {
}
