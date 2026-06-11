package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardSummaryResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_REGISTRATION)
        UsageSummary registration,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_VERIFY_BY_ID)
        UsageSummary verifyById,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_VERIFY_BY_IMAGE)
        UsageSummary verifyByImage,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_IDENTIFY)
        UsageSummary identify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_LIVENESS)
        UsageSummary liveness
) {

    public record UsageSummary(
            @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_PERIOD_COUNT)
            long periodCount,
            @Schema(description = SwaggerDescriptions.DASHBOARD_USAGE_TOTAL_COUNT)
            long totalCount
    ) {}

    public static DashboardSummaryResponse from(DashboardSummaryResult result) {
        return new DashboardSummaryResponse(
                new UsageSummary(result.registrationPeriodCount(),  result.registrationTotalCount()),
                new UsageSummary(result.verifyByIdPeriodCount(),    result.verifyByIdTotalCount()),
                new UsageSummary(result.verifyByImagePeriodCount(), result.verifyByImageTotalCount()),
                new UsageSummary(result.identifyPeriodCount(),      result.identifyTotalCount()),
                new UsageSummary(result.livenessPeriodCount(),      result.livenessTotalCount())
        );
    }
}
