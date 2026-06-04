package ai.univs.gate.facade.dashboard.application.result;

import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;

import java.util.List;

public record DashboardTrendResult(
        TrendPeriod period,
        List<String> labels, // TODAY: "HH" (00~23)  WEEK/MONTH: "yyyy-MM-dd"  YEAR: "yyyy-MM"
        List<Long> registration,
        List<Long> verifyById,
        List<Long> verifyByImage,
        List<Long> identify,
        List<Long> liveness,
        List<Long> palmRegistration,
        List<Long> palmIdentify,
        List<Long> palmLiveness
) {
}
