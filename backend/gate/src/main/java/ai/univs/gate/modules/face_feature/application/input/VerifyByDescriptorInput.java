package ai.univs.gate.modules.face_feature.application.input;

public record VerifyByDescriptorInput(
        String apiKey,
        Long accountId,
        String descriptor,
        String targetDescriptor,
        String transactionUuid
) {
}
