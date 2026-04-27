package ai.univs.auth.api.dto;

import ai.univs.auth.application.result.LoginResult;
import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponseDTO(
        @Schema(description = SwaggerDescriptions.ACCESS_TOKEN)
        String accessToken,
        @Schema(description = SwaggerDescriptions.REFRESH_TOKEN)
        String refreshToken,
        @Schema(description = SwaggerDescriptions.TOKEN_TYPE)
        String tokenType,
        @Schema(description = SwaggerDescriptions.TOKEN_EXPIRES_IN)
        int expiresIn,
        AccountResponseDTO accountResponseDTO
) {

    public static LoginResponseDTO of(LoginResult result) {
        return new LoginResponseDTO(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn(),
                AccountResponseDTO.of(result.accountResult()));
    }
}
