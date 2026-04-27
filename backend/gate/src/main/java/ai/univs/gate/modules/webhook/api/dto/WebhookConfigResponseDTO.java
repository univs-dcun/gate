package ai.univs.gate.modules.webhook.api.dto;

import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record WebhookConfigResponseDTO(
        @Schema(description = SwaggerDescriptions.WEBHOOK_CONFIG_ID)
        Long webhookConfigId,
        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,
        @Schema(description = SwaggerDescriptions.WEBHOOK_URL)
        String webhookUrl,
        @Schema(description = SwaggerDescriptions.WEBHOOK_DEMO_ENABLED)
        Boolean demoEnabled,
        @Schema(description = SwaggerDescriptions.WEBHOOK_SDK_ENABLED)
        Boolean sdkEnabled,
        @Schema(description = SwaggerDescriptions.WEBHOOK_API_ENABLED)
        Boolean apiEnabled
) {
    public static WebhookConfigResponseDTO from(WebhookConfigResult result) {
        return new WebhookConfigResponseDTO(
                result.webhookConfigId(),
                result.projectId(),
                result.webhookUrl(),
                result.demoEnabled(),
                result.sdkEnabled(),
                result.apiEnabled()
        );
    }
}
