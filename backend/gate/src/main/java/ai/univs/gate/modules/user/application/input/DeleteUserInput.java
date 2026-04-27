package ai.univs.gate.modules.user.application.input;

public record DeleteUserInput(
        Long accountId,
        String apiKey,
        Long userId
) {
}
