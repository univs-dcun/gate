package ai.univs.gate.modules.palm_media.application.input;

public record GetPalmMediaInput(
        Long accountId,
        String apiKey,
        Long palmMediaId
) {}
