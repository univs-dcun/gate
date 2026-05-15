package ai.univs.gate.facade.dashboard.application.result;

public record DashboardSummaryResult(
        long registrationPeriodCount,
        long registrationTotalCount,
        long verifyByIdPeriodCount,
        long verifyByIdTotalCount,
        long verifyByImagePeriodCount,
        long verifyByImageTotalCount,
        long identifyPeriodCount,
        long identifyTotalCount,
        long livenessPeriodCount,
        long livenessTotalCount
) {
}
