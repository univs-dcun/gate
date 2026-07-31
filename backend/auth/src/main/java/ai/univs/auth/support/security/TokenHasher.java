package ai.univs.auth.support.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 리프레시 토큰을 DB에 저장하기 전 단방향 해시로 변환한다 (UG-242).
 * DB(refresh_tokens)가 유출되어도 원문 토큰을 복원할 수 없게 하여 세션 탈취를 방지한다.
 * 토큰 자체가 고엔트로피 서명 JWT이므로 salt 없는 SHA-256으로 충분하다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에서 보장되므로 실질적으로 도달하지 않는다.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
