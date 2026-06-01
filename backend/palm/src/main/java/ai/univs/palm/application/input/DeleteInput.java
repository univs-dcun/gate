package ai.univs.palm.application.input;

public record DeleteInput(
        String branchName,
        String palmId,
        String transactionUuid,
        String clientId
) {
}
