package ai.univs.gate.modules.webhook.domain.entity;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "webhook_configs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_config_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    @Column(name = "demo_enabled", nullable = false)
    private Boolean demoEnabled;

    @Column(name = "api_enabled", nullable = false)
    private Boolean apiEnabled;

    /**
     * 서명 키 (UG-344). 수신 측이 같은 키로 {@code X-Gate-Signature} 를 검증한다. 서명하려면 원문이 필요해 평문으로
     * 둔다 — API 키의 secret 과 같은 취급이다. 로그에 남기지 않는다.
     *
     * <p>이 컬럼이 생기기 전의 설정은 비어 있다. 설정을 저장·조회하거나 웹훅을 보낼 때 채운다 (V34 주석).
     */
    @Column(name = "webhook_secret", length = 100)
    private String webhookSecret;

    /** 재발급 직전의 키. {@link #previousSecretExpiresAt} 까지 함께 서명한다 — 수신 측이 끊김 없이 바꾸게 한다. */
    @Column(name = "previous_webhook_secret", length = 100)
    private String previousWebhookSecret;

    /** 옛 키로 서명하는 마지막 시각 (UTC). */
    @Column(name = "previous_secret_expires_at")
    private LocalDateTime previousSecretExpiresAt;

    public void update(String webhookUrl,
                       Boolean demoEnabled,
                       Boolean apiEnabled
    ) {
        this.webhookUrl = webhookUrl;
        this.demoEnabled = demoEnabled;
        this.apiEnabled = apiEnabled;
    }

    /** 키가 없을 때만 넣는다. 행을 잠근 트랜잭션에서 부른다 — 잠그지 않으면 전송 쪽이 넣은 키를 덮어쓸 수 있다. */
    public void assignSecretIfAbsent(String secret) {
        if (this.webhookSecret == null) {
            this.webhookSecret = secret;
        }
    }

    /**
     * 키를 새로 발급한다 (UG-344). 지금 키는 {@code overlap} 동안 옛 키로 남아 함께 서명된다.
     *
     * <p>겹치는 동안 다시 재발급하면 그때의 지금 키가 옛 키가 되고, 그 전의 옛 키는 바로 버린다 — 동시에 서명하는
     * 키는 최대 둘이다.
     */
    public void rotateSecret(String newSecret, LocalDateTime nowUtc, Duration overlap) {
        if (this.webhookSecret != null) {
            this.previousWebhookSecret = this.webhookSecret;
            this.previousSecretExpiresAt = nowUtc.plus(overlap);
        }
        this.webhookSecret = newSecret;
    }

    /** 지금 서명에 쓸 키들. 새 키가 먼저다. */
    public List<String> activeSecrets(LocalDateTime nowUtc) {
        List<String> secrets = new ArrayList<>(2);
        if (webhookSecret != null) {
            secrets.add(webhookSecret);
        }
        if (activePreviousSecretExpiresAt(nowUtc) != null) {
            secrets.add(previousWebhookSecret);
        }
        return secrets;
    }

    /** 옛 키가 아직 쓰이고 있으면 그 만료 시각, 아니면 null. */
    public LocalDateTime activePreviousSecretExpiresAt(LocalDateTime nowUtc) {
        if (previousWebhookSecret == null || previousSecretExpiresAt == null
                || !previousSecretExpiresAt.isAfter(nowUtc)) {
            return null;
        }
        return previousSecretExpiresAt;
    }
}
