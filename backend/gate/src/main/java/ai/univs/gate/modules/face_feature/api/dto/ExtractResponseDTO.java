package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.modules.face_feature.application.result.ExtractResult;
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
