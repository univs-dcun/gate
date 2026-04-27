package ai.univs.gate.facade.sdk.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record QrCodeResponseDTO(
        @Schema(description = SwaggerDescriptions.BASE64_QR_CODE)
        String base64QrCode,

        @Schema(description = SwaggerDescriptions.QR_LINK)
        String link
) {
}
