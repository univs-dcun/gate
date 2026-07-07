package ai.univs.gate.modules.feature.api.dto.palm;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.dto.CustomPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PalmFeaturesResponseDTO(
        @Schema(description = SwaggerDescriptions.PALM_FEATURE_LIST)
        List<PalmFeatureResponseDTO> palmFeatures,

        @Schema(description = SwaggerDescriptions.PAGE_INFO)
        CustomPage page
) {}
