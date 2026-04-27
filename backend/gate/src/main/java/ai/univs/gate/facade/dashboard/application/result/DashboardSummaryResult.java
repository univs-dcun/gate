package ai.univs.gate.facade.dashboard.application.result;

public record DashboardSummaryResult(
        long registrationCount,      // DB used
        long registrationAllocated,  //DB allocated
        long registrationLimit,      // DB limit

        long verifyCount,      // Verify used
        long verifyAllocated,  // Verify allocated
        long verifyLimit,      // Verify limit

        long identifyCount,      // Identify used
        long identifyAllocated,  // Identify allocated
        long identifyLimit,      // Identify limit

        long livenessCount,      // Liveness used
        long livenessAllocated,  // Liveness allocated
        long livenessLimit       // Liveness limit
) {
}
