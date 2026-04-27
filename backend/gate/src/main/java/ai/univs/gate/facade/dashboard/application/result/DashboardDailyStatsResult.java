package ai.univs.gate.facade.dashboard.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record DashboardDailyStatsResult(
        List<DashboardDailyStatItemResult> items,
        CustomPageResult page
) {
}
