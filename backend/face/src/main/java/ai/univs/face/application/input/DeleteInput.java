package ai.univs.face.application.input;

public record DeleteInput(
        String branchName,
        String faceId,
        String transactionUuid,
        String clientId
) {
}
