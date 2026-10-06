package ai.univs.gate.modules.webhook.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.support.webhook.WebhookTestResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record WebhookTestResponseDTO(
        @Schema(description = SwaggerDescriptions.WEBHOOK_TEST_RESULT,
                allowableValues = {"SUCCESS", "HTTP_ERROR", "TIMEOUT", "CONNECTION_FAILED", "HOST_NOT_FOUND",
                        "TLS_ERROR", "TARGET_NOT_ALLOWED", "TARGET_DENIED_RANGE"})
        String result,

        @Schema(description = SwaggerDescriptions.WEBHOOK_TEST_STATUS_CODE, nullable = true)
        Integer statusCode,

        @Schema(description = SwaggerDescriptions.WEBHOOK_TEST_ELAPSED_MS, nullable = true)
        Long elapsedMs,

        @Schema(description = SwaggerDescriptions.WEBHOOK_TEST_EVENT_ID, nullable = true)
        String eventId
) {
    public static WebhookTestResponseDTO from(WebhookTestResult result) {
        return new WebhookTestResponseDTO(result.result(), result.statusCode(), result.elapsedMs(), result.eventId());
    }
}
