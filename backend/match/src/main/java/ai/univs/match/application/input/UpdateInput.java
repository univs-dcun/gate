package ai.univs.match.application.input;

public record UpdateInput(
        String branchName,
        String faceId,
        String descriptor
) {
}
