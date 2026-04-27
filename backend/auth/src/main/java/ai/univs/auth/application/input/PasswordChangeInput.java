package ai.univs.auth.application.input;

public record PasswordChangeInput(
        Long accountId,
        String password,
        String newPassword
) {
}
