package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-344: 웹훅 서명")
class WebhookSignatureTest {

    private static final long T = 1790658000L;   // 2026-09-29T05:00:00Z
    private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("HMAC-SHA256(키, t + \".\" + 본문) 의 hex — 값은 Python hmac 으로 따로 계산했다 (문서 예제와 같은 방식)")
    void 알려진_값() {
        assertThat(WebhookSignature.header(T, BODY, List.of("whsec_test-key")))
                .isEqualTo("t=1790658000,v1=389db323ae036d3567e6158b1fd628ece7a31c640e990cb0caffbd89f7c1e623");
    }

    @Test
    @DisplayName("키가 둘이면 v1 이 둘이고 새 키가 먼저다 — 재발급 후 겹치는 기간")
    void 두_키() {
        assertThat(WebhookSignature.header(T, BODY, List.of("whsec_test-key", "whsec_old-key")))
                .isEqualTo("t=1790658000"
                        + ",v1=389db323ae036d3567e6158b1fd628ece7a31c640e990cb0caffbd89f7c1e623"
                        + ",v1=3164ff7ad584eeace8cea98bff03d78e16c7c1cee33e4ede7000be92bdebd5d4");
    }

    @Test
    @DisplayName("타임스탬프가 서명에 들어간다 — 같은 본문도 t 가 다르면 서명이 다르다(재전송 방어)")
    void 타임스탬프_포함() {
        assertThat(WebhookSignature.sign("whsec_test-key", T, BODY))
                .isNotEqualTo(WebhookSignature.sign("whsec_test-key", T + 1, BODY));
    }

    @Test
    @DisplayName("키 없이 서명하라고 하면 거부한다 — 빈 헤더를 만들지 않는다")
    void 키_없음() {
        assertThatThrownBy(() -> WebhookSignature.header(T, BODY, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("발급 키는 whsec_ + base64url 43자(256비트)이고 매번 다르다")
    void 키_형식() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String secret = WebhookSecrets.generate();
            assertThat(secret).matches("whsec_[A-Za-z0-9_-]{43}");
            seen.add(secret);
        }
        assertThat(seen).hasSize(100);
    }
}
