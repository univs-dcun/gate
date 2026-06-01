package ai.univs.palm.application.result;

public record DeleteResult(
        String branchName,
        String palmId,
        String transactionUuid
) {
}
