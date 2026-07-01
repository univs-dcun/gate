package ai.univs.gate.facade.feature.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.dto.CustomPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FeatureListResponse(
        @Schema(description = SwaggerDescriptions.FEATURE_LIST_COMBINED)
        List<FeatureItemResponse> features,

        @Schema(description = SwaggerDescriptions.PAGE_INFO)
        CustomPage page
) {}
