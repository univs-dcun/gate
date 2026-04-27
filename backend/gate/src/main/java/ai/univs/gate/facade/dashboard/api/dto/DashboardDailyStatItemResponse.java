package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatItemResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardDailyStatItemResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_DAILY_STAT_DATE)
        String date,
        @Schema(description = SwaggerDescriptions.COUNT_USER_REGISTRATION)
        long registration,
        @Schema(description = SwaggerDescriptions.COUNT_VERIFY)
        long verify,
        @Schema(description = SwaggerDescriptions.COUNT_IDENTIFY)
        long identify,
        @Schema(description = SwaggerDescriptions.COUNT_LIVENESS)
        long liveness
) {

    public static DashboardDailyStatItemResponse from(DashboardDailyStatItemResult item) {
        return new DashboardDailyStatItemResponse(
                item.date(),
                item.registration(),
                item.verify(),
                item.identify(),
                item.liveness()
        );
    }
}
