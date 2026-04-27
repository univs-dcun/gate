package ai.univs.gate.modules.webhook.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record WebhookConfigRequestDTO(
        @Schema(description = SwaggerDescriptions.WEBHOOK_URL)
        @NotBlank
        @Length(max = 500)
        String webhookUrl,

        @Schema(description = SwaggerDescriptions.WEBHOOK_DEMO_ENABLED)
        @NotNull
        Boolean demoEnabled,

        @Schema(description = SwaggerDescriptions.WEBHOOK_SDK_ENABLED)
        @NotNull
        Boolean sdkEnabled,

        @Schema(description = SwaggerDescriptions.WEBHOOK_API_ENABLED)
        @NotNull
        Boolean apiEnabled
) {
}
