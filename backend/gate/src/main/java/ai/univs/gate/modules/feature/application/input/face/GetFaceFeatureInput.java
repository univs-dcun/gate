package ai.univs.gate.modules.feature.application.input.face;

public record GetFaceFeatureInput(
        Long accountId,
        String apiKey,
        Long faceFeatureId
) {
}
