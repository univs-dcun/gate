package ai.univs.gate.facade.dashboard.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.dashboard.api.dto.DashboardDailyStatsRequest;
import ai.univs.gate.facade.dashboard.api.dto.DashboardPeriodRequest;
import ai.univs.gate.facade.dashboard.api.dto.DashboardTrendRequest;
import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardDailyStatsUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardRatiosUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardSummaryUseCase;
import ai.univs.gate.facade.dashboard.application.usecase.GetDashboardTrendUseCase;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 대시보드 컨트롤러의 배선 (UG-301 반박 리뷰 F5).
 *
 * <p>이 컨트롤러에는 테스트가 하나도 없었다. 반박 리뷰가 변이 둘로 그것을 드러냈다 —
 * {@code /daily} 의 {@code effectivePage()}/{@code effectivePageSize()} 를 자리 바꿈 해도,
 * {@code @Transactional} 을 떼도 전 테스트가 초록이었다.
 *
 * <p>UseCase 테스트가 같은 자리 바꿈을 잡는다고 주석에 써 뒀지만, <b>같은 버그가 컨트롤러에
 * 있으면 아무도 안 잡는다.</b> 계층이 다르면 테스트도 따로 있어야 한다.
 *
 * <p>{@code UserContext} 는 ThreadLocal 이라 앱을 띄우지 않고도 채울 수 있다. 실제로는
 * {@code UserContextInterceptor} 가 헤더에서 채우지만, 여기서는 그 결과만 있으면 된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드 컨트롤러 배선")
class DashboardControllerTest {

    private static final long ACCOUNT = 100L;
    private static final String KEY = "univs_live_abcdefghijklmnop";
    /** page 와 pageSize 는 반드시 달라야 한다 — 같으면 자리 바뀜을 못 잡는다. */
    private static final int PAGE = 3;
    private static final int PAGE_SIZE = 50;

    @Mock
    private GetDashboardSummaryUseCase getDashboardSummaryUseCase;
    @Mock
    private GetDashboardTrendUseCase getDashboardTrendUseCase;
    @Mock
    private GetDashboardDailyStatsUseCase getDashboardDailyStatsUseCase;
    @Mock
    private GetDashboardRatiosUseCase getDashboardRatiosUseCase;

    @InjectMocks
    private DashboardController dashboardController;

    @BeforeEach
    void setUp() {
        UserContext.set(UserContext.builder()
                .accountId(String.valueOf(ACCOUNT))
                .apiKey(KEY)
                .build());
    }

    @AfterEach
    void tearDown() {
        // ThreadLocal 이라 안 지우면 같은 스레드에서 도는 다음 테스트로 샌다.
        UserContext.clear();
    }

    @Test
    @DisplayName("/daily 가 page 와 pageSize 를 자리에 맞게 넘긴다")
    void daily_페이징_인자() {
        given(getDashboardDailyStatsUseCase.execute(ACCOUNT, KEY, PAGE, PAGE_SIZE, FeatureType.PALM))
                .willReturn(new DashboardDailyStatsResult(
                        List.of(), new CustomPageResult(PAGE_SIZE, PAGE, 0L, 0, 0L)));

        dashboardController.getDailyStats(
                new DashboardDailyStatsRequest(PAGE, PAGE_SIZE, FeatureType.PALM));

        // 자리를 바꾸면 클라이언트가 3페이지를 요청했는데 50페이지가 나간다.
        verify(getDashboardDailyStatsUseCase)
                .execute(ACCOUNT, KEY, PAGE, PAGE_SIZE, FeatureType.PALM);
    }

    @Test
    @DisplayName("/summary 가 컨텍스트의 계정·키와 요청 파라미터를 그대로 넘긴다")
    void summary_인자() {
        given(getDashboardSummaryUseCase.execute(
                eq(ACCOUNT), eq(KEY), eq(TrendPeriod.YEAR), eq(FeatureType.PALM)))
                .willReturn(new DashboardSummaryResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        dashboardController.getSummary(new DashboardPeriodRequest(TrendPeriod.YEAR, FeatureType.PALM));

        // accountId 를 상수로 굳히거나 다른 계정을 넘기면 테넌트 격리가 통째로 사라진다.
        verify(getDashboardSummaryUseCase)
                .execute(ACCOUNT, KEY, TrendPeriod.YEAR, FeatureType.PALM);
    }

    @Test
    @DisplayName("/trend 가 기본값(WEEK/FACE)을 적용해 넘긴다")
    void trend_기본값() {
        given(getDashboardTrendUseCase.execute(ACCOUNT, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .willReturn(new DashboardTrendResult(TrendPeriod.WEEK, List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of()));

        // 둘 다 null — effectivePeriod()/effectiveFeatureType() 이 도는 자리다.
        dashboardController.getTrend(new DashboardTrendRequest(null, null));

        verify(getDashboardTrendUseCase).execute(ACCOUNT, KEY, TrendPeriod.WEEK, FeatureType.FACE);
    }

    @Test
    @DisplayName("/ratios 가 기본값(MONTH/FACE)을 적용해 넘긴다")
    void ratios_기본값() {
        // /trend 와 기본 period 가 다르다. 컨트롤러가 두 DTO 를 바꿔 쓰면 여기서 깨진다.
        given(getDashboardRatiosUseCase.execute(ACCOUNT, KEY, TrendPeriod.MONTH, FeatureType.FACE))
                .willReturn(new DashboardRatiosResult(
                        new DashboardRatiosResult.RatioItem(0, 0),
                        new DashboardRatiosResult.RatioItem(0, 0),
                        new DashboardRatiosResult.RatioItem(0, 0),
                        new DashboardRatiosResult.RatioItem(0, 0),
                        new DashboardRatiosResult.RatioItem(0, 0)));

        dashboardController.getRatios(new DashboardPeriodRequest(null, null));

        verify(getDashboardRatiosUseCase).execute(ACCOUNT, KEY, TrendPeriod.MONTH, FeatureType.FACE);
    }
}
