package ai.univs.auth.api.dto;

import ai.univs.auth.application.result.SignupResult;
import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import ai.univs.auth.shared.web.ctx.TimeZoneContextHolder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.auth.shared.utils.DateTimeUtil.fromUtc;

public record SignupResponseDTO(
        @Schema(description = SwaggerDescriptions.ACCOUNT_ID)
        Long accountId,
        @Schema(description = SwaggerDescriptions.EMAIL)
        String email,
        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt
) {

    public static SignupResponseDTO from(SignupResult result) {
        return new SignupResponseDTO(
                result.accountId(),
                result.email(),
                fromUtc(result.createdAt(), TimeZoneContextHolder.get()));
    }
}
