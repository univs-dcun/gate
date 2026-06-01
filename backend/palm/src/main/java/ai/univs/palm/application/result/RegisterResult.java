package ai.univs.palm.application.result;

public record RegisterResult(
        String branchName,
        String palmId,
        String transactionUuid
) {
}
