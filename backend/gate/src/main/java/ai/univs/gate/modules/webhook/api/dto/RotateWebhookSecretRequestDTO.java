package ai.univs.gate.modules.webhook.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

/** 서명 키 재발급 선택 (UG-344). 본문을 생략하면 24시간 함께 사용이다. */
public record RotateWebhookSecretRequestDTO(
        @Schema(description = SwaggerDescriptions.WEBHOOK_REVOKE_PREVIOUS, nullable = true, defaultValue = "false")
        Boolean revokePrevious
) {
}
