package ai.univs.gate.modules.feature.application.input.face;

public record GetFaceFeatureByFeatureIdInput(
        Long accountId,
        String apiKey,
        String featureId
) {
}
