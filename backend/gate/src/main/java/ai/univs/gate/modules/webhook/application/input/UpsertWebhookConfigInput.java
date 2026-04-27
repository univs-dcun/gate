package ai.univs.gate.modules.webhook.application.input;

public record UpsertWebhookConfigInput(
        Long accountId,
        Long projectId,
        String webhookUrl,
        Boolean demoEnabled,
        Boolean sdkEnabled,
        Boolean apiEnabled
) {
}
