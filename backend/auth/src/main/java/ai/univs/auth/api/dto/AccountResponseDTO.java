package ai.univs.auth.api.dto;

import ai.univs.auth.application.result.AccountResult;
import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import ai.univs.auth.shared.web.ctx.TimeZoneContextHolder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.auth.shared.utils.DateTimeUtil.fromUtc;

public record AccountResponseDTO(
        @Schema(description = SwaggerDescriptions.ACCOUNT_ID)
        long accountId,
        @Schema(description = SwaggerDescriptions.EMAIL)
        String email,
        @Schema(description = SwaggerDescriptions.LAST_LOGIN_AT)
        LocalDateTime lastLoginAt
) {

    public static AccountResponseDTO of(AccountResult result) {
        return new AccountResponseDTO(
                result.accountId(),
                result.email(),
                fromUtc(result.lastLoginAt(), TimeZoneContextHolder.get()));
    }
}
