package ai.univs.gate.support.webhook;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 웹훅 서명 키 발급 (UG-344).
 *
 * <p>{@code whsec_} 뒤에 무작위 32바이트(256비트)를 base64url(패딩 없음)로 붙인다. 접두어는 사람이 API 키와
 * 헷갈리지 않게 하려는 것이다. 서명에는 <b>접두어를 포함한 문자열 전체</b>를 UTF-8 바이트로 키로 쓴다 — 수신 측이
 * 받은 문자열을 그대로 쓰면 된다.
 */
public final class WebhookSecrets {

    static final String PREFIX = "whsec_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSecrets() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
