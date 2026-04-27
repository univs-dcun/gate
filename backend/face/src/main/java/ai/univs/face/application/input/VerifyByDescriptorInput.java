package ai.univs.face.application.input;

public record VerifyByDescriptorInput(
        String descriptor,
        String targetDescriptor,
        String transactionUuid,
        String clientId
) {
}
