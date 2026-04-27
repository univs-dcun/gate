package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardSummaryResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_REGISTRATION)
        UsageSummary registration,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_VERIFY)
        UsageSummary verify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_IDENTIFY)
        UsageSummary identify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_LIVENESS)
        UsageSummary liveness
) {

    public record UsageSummary(
            @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_TOTAL_COUNT)
            long totalCount,
            @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_ALLOCATED)
            long allocated,
            @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_REMAINING)
            long remaining,
            @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_PERCENT)
            int usagePercent
    ) {}

    public static DashboardSummaryResponse from(DashboardSummaryResult result) {
        return new DashboardSummaryResponse(
                toUsageSummary(result.registrationCount(), result.registrationAllocated(), result.registrationLimit()),
                toUsageSummary(result.verifyCount(),       result.verifyAllocated(),       result.verifyLimit()),
                toUsageSummary(result.identifyCount(),     result.identifyAllocated(),     result.identifyLimit()),
                toUsageSummary(result.livenessCount(),     result.livenessAllocated(),     result.livenessLimit())
        );
    }

    private static UsageSummary toUsageSummary(long count, long allocated, long remaining) {
        int percent = allocated == 0
                ? 0
                : (int) Math.min(100L, (allocated - remaining) * 100L / allocated);

        return new UsageSummary(count, allocated, remaining, percent);
    }
}
