package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DashboardTrendResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_PERIOD)
        String period, // "WEEK" | "MONTH" | "YEAR"
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_LABELS)
        List<String> labels, // WEEK/MONTH: "yyyy-MM-dd"  YEAR: "yyyy-MM"
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_REGISTRATION)
        List<Long> registration,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_VERIFY_BY_ID)
        List<Long> verifyById,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_VERIFY_BY_IMAGE)
        List<Long> verifyByImage,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_IDENTIFY)
        List<Long> identify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_TREND_LIVENESS)
        List<Long> liveness
) {

    public static DashboardTrendResponse from(DashboardTrendResult result) {
        return new DashboardTrendResponse(
                result.period().name(),
                result.labels(),
                result.registration(),
                result.verifyById(),
                result.verifyByImage(),
                result.identify(),
                result.liveness());
    }
}
