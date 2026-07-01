package ai.univs.gate.modules.feature.application.input.face;

public record VerifyByDescriptorInput(
        String apiKey,
        Long accountId,
        String descriptor,
        String targetDescriptor,
        String transactionUuid
) {}