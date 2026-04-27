package ai.univs.face.application.result;

public record VerifyByImageResult(
        String transactionUuid,
        String similarity,
        String threshold,
        boolean result
) {
}
