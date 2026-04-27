package ai.univs.face.application.result;

public record IdentifyResult(
        String transactionUuid,
        String faceId,
        String similarity,
        String threshold,
        boolean result
) {
}
