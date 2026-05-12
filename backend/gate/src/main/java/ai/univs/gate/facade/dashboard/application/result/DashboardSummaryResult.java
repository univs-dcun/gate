package ai.univs.gate.facade.dashboard.application.result;

public record DashboardSummaryResult(
        long registrationCount,
        long verifyCount,
        long identifyCount,
        long livenessCount
) {
}
