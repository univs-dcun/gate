package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.domain.enums.DashboardMediaType;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardPeriodRequest(
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_PERIOD, defaultValue = "MONTH")
        TrendPeriod period,

        @Schema(description = "미디어 타입 (FACE | PALM)", defaultValue = "FACE")
        DashboardMediaType mediaType
) {

    public TrendPeriod effectivePeriod() {
        return period != null ? period : TrendPeriod.MONTH;
    }

    public DashboardMediaType effectiveMediaType() {
        return mediaType != null ? mediaType : DashboardMediaType.FACE;
    }
}
