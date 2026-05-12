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
                new UsageSummary(result.registrationCount(), 0, 0, 0),
                new UsageSummary(result.verifyCount(),       0, 0, 0),
                new UsageSummary(result.identifyCount(),     0, 0, 0),
                new UsageSummary(result.livenessCount(),     0, 0, 0)
        );
    }
}
