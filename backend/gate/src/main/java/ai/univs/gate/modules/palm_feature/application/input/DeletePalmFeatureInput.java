package ai.univs.gate.modules.palm_feature.application.input;

public record DeletePalmFeatureInput(
        Long accountId,
        String apiKey,
        Long palmFeatureId
) {}
