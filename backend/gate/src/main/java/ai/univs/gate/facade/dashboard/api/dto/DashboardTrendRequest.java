package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardTrendRequest(
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_PERIOD, defaultValue = "WEEK")
        TrendPeriod period,

        @Schema(description = SwaggerDescriptions.DASHBOARD_FEATURE_TYPE, defaultValue = "FACE")
        FeatureType featureType
) {

    public TrendPeriod effectivePeriod() {
        return period != null ? period : TrendPeriod.WEEK;
    }

    public FeatureType effectiveFeatureType() {
        return featureType != null ? featureType : FeatureType.FACE;
    }
}
