package ai.univs.gate.modules.feature.application.input;

public record UpdateFeatureDescriptionInput(
        Long accountId,
        String apiKey,
        Long featureId,
        String description
) {}
