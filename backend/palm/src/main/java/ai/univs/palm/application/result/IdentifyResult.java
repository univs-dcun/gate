package ai.univs.palm.application.result;

public record IdentifyResult(
        String transactionUuid,
        String palmId,
        String similarity,
        String threshold,
        boolean result
) {
}
