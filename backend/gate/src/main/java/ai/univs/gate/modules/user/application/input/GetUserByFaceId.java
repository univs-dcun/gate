package ai.univs.gate.modules.user.application.input;

public record GetUserByFaceId(
        Long accountId,
        String apiKey,
        String faceId
) {
}
