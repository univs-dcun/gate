package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardRatiosResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_REGISTRATION)
        RatioSummary registration,
        @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_VERIFY)
        RatioSummary verify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_IDENTIFY)
        RatioSummary identify,
        @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_LIVENESS)
        RatioSummary liveness
) {

    public record RatioSummary(
            @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_SUMMARY_PP)
            int primaryPercent,
            @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_SUMMARY_SP)
            int secondaryPercent,
            @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_SUMMARY_PC)
            long primaryCount,
            @Schema(description = SwaggerDescriptions.DASHBOARD_RATIO_SUMMARY_SC)
            long secondaryCount
    ) {}

    public static DashboardRatiosResponse from(DashboardRatiosResult result) {
        return new DashboardRatiosResponse(
                toRatioSummary(result.registration()),
                toRatioSummary(result.verify()),
                toRatioSummary(result.identify()),
                toRatioSummary(result.liveness()));
    }

    private static RatioSummary toRatioSummary(DashboardRatiosResult.RatioItem item) {
        long total = item.primaryCount() + item.secondaryCount();
        if (total == 0) {
            return new RatioSummary(0, 0, 0, 0);
        }
        int primaryPercent = (int) Math.round(item.primaryCount() * 100.0 / total);

        return new RatioSummary(
                primaryPercent,
                100 - primaryPercent,
                item.primaryCount(),
                item.secondaryCount());
    }
}
