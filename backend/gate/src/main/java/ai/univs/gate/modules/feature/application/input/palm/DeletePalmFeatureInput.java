package ai.univs.gate.modules.feature.application.input.palm;

public record DeletePalmFeatureInput(
        Long accountId,
        String apiKey,
        Long palmFeatureId
) {}
