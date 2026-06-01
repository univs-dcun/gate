package ai.univs.gate.facade.dashboard.application.result;

public record DashboardDailyStatItemResult(
        String date, // "yyyy/MM/dd" UTC 기준
        long registration,
        long verifyById,
        long verifyByImage,
        long identify,
        long liveness
) {
}
