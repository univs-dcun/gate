package ai.univs.gate.facade.dashboard.application.result;

public record DashboardDailyStatItemResult(
        String date, // "yyyy/MM/dd" UTC 기준
        long registration,
        long verify,
        long identify,
        long liveness
) {
}
