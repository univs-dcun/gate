package ai.univs.gate.modules.match.application.input;

public record VerifyByDescriptorInput(
        String apiKey,
        Long accountId,
        String descriptor,
        String targetDescriptor,
        String transactionUuid
) {
}
