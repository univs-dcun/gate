package ai.univs.gate.modules.feature.application.result.face;

public record VerifyByDescriptorResult(
        String transactionUuid,
        String similarity,
        boolean result
) {
}
