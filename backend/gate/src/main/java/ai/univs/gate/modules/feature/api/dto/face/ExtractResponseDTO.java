package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.result.face.ExtractResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record ExtractResponseDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR)
        String descriptor
) {

    public static ExtractResponseDTO from(ExtractResult result) {
        return new ExtractResponseDTO(result.descriptor());
    }
}
