package ai.univs.gate.modules.feature.application.input.palm;

public record GetPalmFeatureInput(
        Long accountId,
        String apiKey,
        Long palmFeatureId
) {}
