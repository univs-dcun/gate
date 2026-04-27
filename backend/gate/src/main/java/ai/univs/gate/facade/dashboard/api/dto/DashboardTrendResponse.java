package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DashboardTrendResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_PERIOD, defaultValue = "WEEK")
        String period, // "WEEK" | "MONTH" | "YEAR"
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_LABELS, defaultValue = "WEEK")
        List<String> labels, // WEEK/MONTH: "yyyy-MM-dd"  YEAR: "yyyy-MM"
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_REGISTRATION, defaultValue = "WEEK")
        List<Long> registration,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_VERIFY, defaultValue = "WEEK")
        List<Long> verify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_IDENTIFY, defaultValue = "WEEK")
        List<Long> identify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_LIVENESS, defaultValue = "WEEK")
        List<Long> liveness
) {

    public static DashboardTrendResponse from(DashboardTrendResult result) {
        return new DashboardTrendResponse(
                result.period().name(),
                result.labels(),
                result.registration(),
                result.verify(),
                result.identify(),
                result.liveness());
    }
}
