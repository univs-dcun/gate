package ai.univs.gate.modules.webhook.application.result;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;

public record WebhookConfigResult(
        Long webhookConfigId,
        Long projectId,
        String webhookUrl,
        Boolean demoEnabled,
        Boolean sdkEnabled,
        Boolean apiEnabled
) {
    public static WebhookConfigResult from(WebhookConfig config) {
        return new WebhookConfigResult(
                config.getId(),
                config.getProject().getId(),
                config.getWebhookUrl(),
                config.getDemoEnabled(),
                config.getSdkEnabled(),
                config.getApiEnabled()
        );
    }
}
