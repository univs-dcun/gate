package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DashboardDailyStatsRequest(
        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "10")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer pageSize,

        @Schema(description = SwaggerDescriptions.DASHBOARD_FEATURE_TYPE, defaultValue = "FACE")
        FeatureType featureType
) {

    public int effectivePage() {
        return page != null ? page : 1;
    }

    public int effectivePageSize() {
        return pageSize != null ? pageSize : 10;
    }

    public FeatureType effectiveFeatureType() {
        return featureType != null ? featureType : FeatureType.FACE;
    }
}
