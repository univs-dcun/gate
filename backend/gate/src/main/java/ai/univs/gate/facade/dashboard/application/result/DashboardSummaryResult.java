package ai.univs.gate.facade.dashboard.application.result;

public record DashboardSummaryResult(
        long registrationCount,
        long verifyByIdCount,
        long verifyByImageCount,
        long identifyCount,
        long livenessCount
) {
}
