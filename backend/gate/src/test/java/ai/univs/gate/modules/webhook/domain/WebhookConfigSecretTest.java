package ai.univs.gate.modules.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-344: 웹훅 서명 키 교체")
class WebhookConfigSecretTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 3, 0);
    private static final Duration DAY = Duration.ofHours(24);

    private WebhookConfig config(String secret) {
        return WebhookConfig.builder().webhookUrl("https://8.8.8.8/h").demoEnabled(false).apiEnabled(true)
                .webhookSecret(secret).build();
    }

    @Test
    @DisplayName("재발급하면 새 키가 먼저, 옛 키가 24시간 함께 서명한다")
    void 재발급() {
        WebhookConfig c = config("old");

        c.rotateSecret("new", NOW, DAY);

        assertThat(c.getWebhookSecret()).isEqualTo("new");
        assertThat(c.activeSecrets(NOW)).containsExactly("new", "old");
        assertThat(c.activePreviousSecretExpiresAt(NOW)).isEqualTo(NOW.plusHours(24));
    }

    @Test
    @DisplayName("24시간이 지나면 옛 키는 쓰지 않는다 — 만료 시각 자체도 포함하지 않는다")
    void 만료() {
        WebhookConfig c = config("old");
        c.rotateSecret("new", NOW, DAY);

        assertThat(c.activeSecrets(NOW.plusHours(24))).containsExactly("new");
        assertThat(c.activePreviousSecretExpiresAt(NOW.plusHours(24))).isNull();
        assertThat(c.activeSecrets(NOW.plusHours(24).minusSeconds(1))).containsExactly("new", "old");
    }

    @Test
    @DisplayName("겹치는 동안 다시 재발급하면 바로 전 키만 남는다 — 동시에 서명하는 키는 최대 둘")
    void 연속_재발급() {
        WebhookConfig c = config("k1");
        c.rotateSecret("k2", NOW, DAY);

        c.rotateSecret("k3", NOW.plusHours(1), DAY);

        assertThat(c.activeSecrets(NOW.plusHours(1))).containsExactly("k3", "k2");
        assertThat(c.activePreviousSecretExpiresAt(NOW.plusHours(1))).isEqualTo(NOW.plusHours(25));
    }

    @Test
    @DisplayName("키가 없던 설정을 재발급하면 옛 키 없이 새 키만 생긴다")
    void 키_없던_설정() {
        WebhookConfig c = config(null);

        c.rotateSecret("new", NOW, DAY);

        assertThat(c.activeSecrets(NOW)).containsExactly("new");
        assertThat(c.activePreviousSecretExpiresAt(NOW)).isNull();
    }

    @Test
    @DisplayName("assignSecretIfAbsent 는 있는 키를 바꾸지 않는다")
    void 채우기() {
        WebhookConfig 없음 = config(null);
        WebhookConfig 있음 = config("keep");

        없음.assignSecretIfAbsent("x");
        있음.assignSecretIfAbsent("x");

        assertThat(없음.getWebhookSecret()).isEqualTo("x");
        assertThat(있음.getWebhookSecret()).isEqualTo("keep");
    }
}
