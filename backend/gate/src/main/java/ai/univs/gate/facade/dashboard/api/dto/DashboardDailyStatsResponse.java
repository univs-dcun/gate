package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record DashboardDailyStatsResponse(
        List<DashboardDailyStatItemResponse> contents,
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
