package ai.univs.face.application.result;

public record DeleteResult(
        String branchName,
        String faceId,
        String transactionUuid
) {
}
