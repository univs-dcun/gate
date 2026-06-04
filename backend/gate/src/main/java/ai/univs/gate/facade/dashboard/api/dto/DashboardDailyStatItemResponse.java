package ai.univs.gate.facade.dashboard.api.dto;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatItemResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardDailyStatItemResponse(
        @Schema(description = SwaggerDescriptions.DASHBOARD_DAILY_STAT_DATE)
        String date,
        @Schema(description = SwaggerDescriptions.COUNT_USER_REGISTRATION)
        long registration,
        @Schema(description = "1:1 촬영 인증 건수 (/verify/id + 레거시)")
        long verifyById,
        @Schema(description = "1:1 사진 인증 건수 (/verify/image)")
        long verifyByImage,
        @Schema(description = SwaggerDescriptions.COUNT_IDENTIFY)
        long identify,
        @Schema(description = SwaggerDescriptions.COUNT_LIVENESS)
        long liveness,
        @Schema(description = "팜 등록 건수")
        long palmRegistration,
        @Schema(description = "팜 1:N 매칭 건수")
        long palmIdentify,
        @Schema(description = "팜 라이브니스 건수")
        long palmLiveness
) {

    public static DashboardDailyStatItemResponse from(DashboardDailyStatItemResult item) {
        return new DashboardDailyStatItemResponse(
                item.date(),
                item.registration(),
                item.verifyById(),
                item.verifyByImage(),
                item.identify(),
                item.liveness(),
                item.palmRegistration(),
                item.palmIdentify(),
                item.palmLiveness()
        );
    }
}
