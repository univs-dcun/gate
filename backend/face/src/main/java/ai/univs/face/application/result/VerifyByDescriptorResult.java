package ai.univs.face.application.result;

public record VerifyByDescriptorResult(
        String transactionUuid,
        String similarity,
        String threshold,
        boolean result
) {
}
