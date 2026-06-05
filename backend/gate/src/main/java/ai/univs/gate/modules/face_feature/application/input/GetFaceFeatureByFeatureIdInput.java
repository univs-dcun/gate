package ai.univs.gate.modules.face_feature.application.input;

public record GetFaceFeatureByFeatureIdInput(
        Long accountId,
        String apiKey,
        String featureId
) {
}
