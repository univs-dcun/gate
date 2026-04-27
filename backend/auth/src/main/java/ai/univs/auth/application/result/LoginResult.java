package ai.univs.auth.application.result;

public record LoginResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresIn,
        AccountResult accountResult
) {

    public static LoginResult of(String accessToken, String refreshToken, AccountResult accountResult) {
        return new LoginResult(accessToken, refreshToken, "Bearer", 900, accountResult);
    }
}
