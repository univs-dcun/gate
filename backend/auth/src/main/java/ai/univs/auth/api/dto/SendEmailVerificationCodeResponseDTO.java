package ai.univs.auth.api.dto;

import ai.univs.auth.application.result.SendEmailVerificationCodeResult;
import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import ai.univs.auth.shared.web.ctx.TimeZoneContextHolder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.auth.shared.utils.DateTimeUtil.fromUtc;

public record SendEmailVerificationCodeResponseDTO(
        @Schema(description = SwaggerDescriptions.EMAIL)
        String email,
        @Schema(description = SwaggerDescriptions.EXPIRES_AT)
        LocalDateTime expiresAt
) {

    public static SendEmailVerificationCodeResponseDTO from(SendEmailVerificationCodeResult result) {
        return new SendEmailVerificationCodeResponseDTO(
                result.email(),
                fromUtc(result.expiresAt(), TimeZoneContextHolder.get()));
    }
}
