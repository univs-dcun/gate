package ai.univs.auth.api.dto;

import ai.univs.auth.application.result.TokenResult;
import ai.univs.auth.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record RepublishAccessTokenResponseDTO(
        @Schema(description = SwaggerDescriptions.ACCESS_TOKEN)
        String accessToken,
        @Schema(description = SwaggerDescriptions.TOKEN_TYPE)
        String tokenType,
        @Schema(description = SwaggerDescriptions.TOKEN_EXPIRES_IN)
        int expiresIn
) {

    public static RepublishAccessTokenResponseDTO from(TokenResult result) {
        return new RepublishAccessTokenResponseDTO(result.accessToken(), result.tokenType(), result.expiresIn());
    }
}
