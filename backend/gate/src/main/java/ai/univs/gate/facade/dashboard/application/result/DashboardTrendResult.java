package ai.univs.gate.facade.dashboard.application.result;

import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;

import java.util.List;

public record DashboardTrendResult(
        TrendPeriod period,
        List<String> labels, // WEEK/MONTH: "yyyy-MM-dd"  YEAR: "yyyy-MM"
        List<Long> registration,
        List<Long> verify,
        List<Long> identify,
        List<Long> liveness
) {
}
