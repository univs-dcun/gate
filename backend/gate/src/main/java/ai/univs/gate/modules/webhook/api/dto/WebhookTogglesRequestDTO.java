package ai.univs.gate.modules.webhook.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

/** 전송 대상 토글만 저장 (UG-344). 바꾼 값만 보낸다 — 둘 다 없으면 PJ-101. */
public record WebhookTogglesRequestDTO(
        @Schema(description = SwaggerDescriptions.WEBHOOK_DEMO_ENABLED, nullable = true)
        Boolean demoEnabled,

        @Schema(description = SwaggerDescriptions.WEBHOOK_API_ENABLED, nullable = true)
        Boolean apiEnabled
) {
}
