package ai.univs.match.application.input;

public record VerifyByFaceIdInput(
        String branchName,
        String faceId,
        String descriptor
) {
}
