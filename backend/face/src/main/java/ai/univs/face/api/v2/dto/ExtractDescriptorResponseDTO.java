package ai.univs.face.api.v2.dto;

import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record ExtractDescriptorResponseDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR)
        String Descriptor
) {

}
