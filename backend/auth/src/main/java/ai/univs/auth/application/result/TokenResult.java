package ai.univs.auth.application.result;

public record TokenResult(
        String accessToken,
        String tokenType,
        int expiresIn
) {

    public static TokenResult of(String accessToken) {
        return new TokenResult(accessToken, "Bearer", 900);
    }
}
