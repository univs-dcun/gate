package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.dto.CustomPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DashboardDailyStatsResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_DAILY_STATS_LIST)
        List<DashboardDailyStatItemResponse> contents,

        @Schema(description = SwaggerDescriptions.PAGE_INFO)
        CustomPage page
) {

    public static DashboardDailyStatsResponse from(DashboardDailyStatsResult result) {
        return new DashboardDailyStatsResponse(
                result.items().stream()
                        .map(DashboardDailyStatItemResponse::from)
                        .toList(),
                CustomPage.from(result.page())
        );
    }
}
