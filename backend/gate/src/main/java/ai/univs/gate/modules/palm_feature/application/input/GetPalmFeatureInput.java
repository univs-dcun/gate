package ai.univs.gate.modules.palm_feature.application.input;

public record GetPalmFeatureInput(
        Long accountId,
        String apiKey,
        Long palmFeatureId
) {}
