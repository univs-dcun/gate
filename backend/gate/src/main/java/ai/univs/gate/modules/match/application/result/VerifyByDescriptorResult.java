package ai.univs.gate.modules.match.application.result;

public record VerifyByDescriptorResult(
        String transactionUuid,
        String similarity,
        boolean result
) {
}
