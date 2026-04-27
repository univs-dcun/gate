package ai.univs.auth.application.input;

public record SignupInput(
        String email,
        String password,
        String passwordConfirm
) {

    public boolean isPasswordMatching() {
        return password != null && password.equals(passwordConfirm);
    }
}
