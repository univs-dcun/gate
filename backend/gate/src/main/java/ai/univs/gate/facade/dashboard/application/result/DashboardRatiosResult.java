package ai.univs.gate.facade.dashboard.application.result;

public record DashboardRatiosResult(
        RatioItem registration,
        RatioItem verify,
        RatioItem identify,
        RatioItem liveness
) {

    public record RatioItem(long primaryCount, long secondaryCount) {}
}
