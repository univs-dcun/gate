package ai.univs.gate.modules.palm_media.application.input;

public record DeletePalmMediaInput(
        Long accountId,
        String apiKey,
        Long palmMediaId
) {}
