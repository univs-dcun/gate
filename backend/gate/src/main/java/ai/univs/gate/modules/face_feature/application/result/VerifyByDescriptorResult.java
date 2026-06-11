package ai.univs.gate.modules.face_feature.application.result;

public record VerifyByDescriptorResult(
        String transactionUuid,
        String similarity,
        boolean result
) {
}
