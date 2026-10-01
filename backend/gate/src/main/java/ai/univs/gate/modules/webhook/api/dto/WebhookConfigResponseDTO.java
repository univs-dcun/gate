package ai.univs.gate.modules.webhook.api.dto;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record WebhookConfigResponseDTO(
        @Schema(description = SwaggerDescriptions.WEBHOOK_CONFIG_ID)
        Long webhookConfigId,

        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,

        @Schema(description = SwaggerDescriptions.WEBHOOK_URL)
        String webhookUrl,

        @Schema(description = SwaggerDescriptions.WEBHOOK_DEMO_ENABLED)
        Boolean demoEnabled,

        @Schema(description = SwaggerDescriptions.WEBHOOK_API_ENABLED)
        Boolean apiEnabled,

        @Schema(description = SwaggerDescriptions.WEBHOOK_SECRET)
        String webhookSecret,

        @Schema(description = SwaggerDescriptions.WEBHOOK_PREVIOUS_SECRET_EXPIRES_AT, nullable = true)
        LocalDateTime previousSecretExpiresAt
) {
    public static WebhookConfigResponseDTO from(WebhookConfigResult result, String timezone) {
        return new WebhookConfigResponseDTO(
                result.webhookConfigId(),
                result.projectId(),
                result.webhookUrl(),
                result.demoEnabled(),
                result.apiEnabled(),
                result.webhookSecret(),
                fromUtc(result.previousSecretExpiresAt(), timezone)
        );
    }
}
