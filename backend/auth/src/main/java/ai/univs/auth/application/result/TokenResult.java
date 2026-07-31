package ai.univs.auth.application.result;

public record TokenResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresIn
) {

    public static TokenResult of(String accessToken, String refreshToken) {
        return new TokenResult(accessToken, refreshToken, "Bearer", 900);
    }
}
