package ai.univs.gate.modules.webhook.application.result;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import java.time.LocalDateTime;

/**
 * @param webhookSecret           서명 키 (UG-344)
 * @param previousSecretExpiresAt 재발급 전 키로도 서명하는 마지막 시각 (UTC). 겹치는 기간이 아니면 null
 */
public record WebhookConfigResult(
        Long webhookConfigId,
        Long projectId,
        String webhookUrl,
        Boolean demoEnabled,
        Boolean apiEnabled,
        String webhookSecret,
        LocalDateTime previousSecretExpiresAt
) {
    public static WebhookConfigResult from(WebhookConfig config, LocalDateTime nowUtc) {
        return new WebhookConfigResult(
                config.getId(),
                config.getProject().getId(),
                config.getWebhookUrl(),
                config.getDemoEnabled(),
                config.getApiEnabled(),
                config.getWebhookSecret(),
                config.activePreviousSecretExpiresAt(nowUtc)
        );
    }
}
