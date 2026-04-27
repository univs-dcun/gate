package ai.univs.face.application.result;

public record RegisterResult(
        String branchName,
        String faceId,
        String transactionUuid
) {
}
