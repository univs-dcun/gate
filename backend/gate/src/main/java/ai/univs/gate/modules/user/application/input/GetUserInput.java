package ai.univs.gate.modules.user.application.input;

public record GetUserInput(
        Long accountId,
        String apiKey,
        Long userId
) {
}
