package ai.univs.gate.facade.dashboard.application.result;

public record DashboardRatiosResult(
        RatioItem registration,
        RatioItem verifyById,
        RatioItem verifyByImage,
        RatioItem identify,
        RatioItem liveness,
        RatioItem palmRegistration,
        RatioItem palmIdentify,
        RatioItem palmLiveness
) {

    public record RatioItem(long primaryCount, long secondaryCount) {}
}
