package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.result.ConsentLogResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ConsentLogResponseDTO(
        @Schema(description = SwaggerDescriptions.CONSENT_LOG_ID)
        Long id,
        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,
        @Schema(description = SwaggerDescriptions.CONSENT_LOG_ACCOUNT_ID)
        Long endUserIdentifier,
        @Schema(description = SwaggerDescriptions.CONSENT_LOG_TYPE)
        String consentType,
        @Schema(description = SwaggerDescriptions.CONSENT_LOG_AGREED)
        Boolean agreed,
        @Schema(description = SwaggerDescriptions.CONSENT_LOG_IP)
        String ipAddress,
        @Schema(description = SwaggerDescriptions.CONSENT_LOG_AGREED_AT)
        LocalDateTime agreedAt,
        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt
) {
    public static ConsentLogResponseDTO from(ConsentLogResult result) {
        return new ConsentLogResponseDTO(
                result.id(),
                result.projectId(),
                result.endUserIdentifier(),
                result.consentType(),
                result.agreed(),
                result.ipAddress(),
                result.agreedAt(),
                result.createdAt()
        );
    }
}
