package ai.univs.face.application.result;

public record VerifyByIdResult(
        String transactionUuid,
        String faceId,
        String similarity,
        String threshold,
        boolean result
) {
}
