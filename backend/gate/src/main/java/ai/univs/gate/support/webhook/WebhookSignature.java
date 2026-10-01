package ai.univs.gate.support.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code X-Gate-Signature} 헤더 값을 만든다 (UG-344).
 *
 * <p>형식: {@code t=<unix 초>,v1=<hex>[,v1=<hex>]}. 서명 대상은 {@code t + "." + 본문 바이트} 이고 알고리즘은
 * HMAC-SHA256 이다 (Stripe 와 같은 구조). 타임스탬프를 서명에 넣어 가로챈 요청을 나중에 다시 보내는 공격을 수신 측이
 * 막을 수 있게 한다. 키 재발급 직후에는 옛 키의 서명도 함께 붙는다 — 수신 측은 어느 하나라도 맞으면 통과시킨다.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-Gate-Signature";
    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    /** @param secrets 서명할 키들(비어 있으면 안 된다). 새 키가 먼저다. */
    public static String header(long timestampSeconds, byte[] body, List<String> secrets) {
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("no signing secret");
        }
        StringBuilder value = new StringBuilder("t=").append(timestampSeconds);
        for (String secret : secrets) {
            value.append(",v1=").append(sign(secret, timestampSeconds, body));
        }
        return value.toString();
    }

    static String sign(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((timestampSeconds + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 은 모든 JDK 가 제공한다 — 여기 오면 런타임이 깨진 것이다
            throw new IllegalStateException(e);
        }
    }
}
