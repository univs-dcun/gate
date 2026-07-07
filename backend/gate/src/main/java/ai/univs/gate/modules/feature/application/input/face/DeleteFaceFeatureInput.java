package ai.univs.gate.modules.feature.application.input.face;

public record DeleteFaceFeatureInput(
        Long accountId,
        String apiKey,
        Long faceFeatureId
) {}
