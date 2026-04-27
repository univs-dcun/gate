package ai.univs.face.application.result;

public record UpdateResult(
        String branchName,
        String faceId,
        String transactionUuid
) {
}
