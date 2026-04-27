package ai.univs.match.application.input;

public record RegisterWithFaceIdInput(
        String branchName,
        String faceId,
        String descriptor
) {
}
