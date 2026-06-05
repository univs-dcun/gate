package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.dto.CustomPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FaceFeaturesResponseDTO(
        @Schema(description = SwaggerDescriptions.FACE_FEATURE_LIST)
        List<FaceFeatureResponseDTO> faceFeatures,
        @Schema(description = SwaggerDescriptions.PAGE_INFO)
        CustomPage page
) {
}
