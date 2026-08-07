package ai.univs.gate.facade.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대시보드 summary 의 접근 통제 (UG-281, UG-288, UG-301).
 *
 * <p>이 UseCase 에는 테스트가 하나도 없었다. UG-288 반박 리뷰가 변이 하나로 그것을 드러냈다 —
 * {@code findOwnedByApiKey(apiKey, accountId)} 를 {@code (apiKey, 0L)} 로 바꿔도 전 테스트가
 * 초록이었다. 즉 테넌트 격리가 통째로 사라져도 아무도 몰랐다.
 *
 * <p><b>UG-301 에서 검증 위치가 바뀌었다.</b> 예전에는 여기서
 * {@code projectService.validateOwnership} 을 한 번 더 불러 LOG_ONLY 를 무력화했다. 지금은
 * {@code ApiKeyService.findStrictlyOwnedByApiKey} 하나로 끝난다. 반박 리뷰가 짚은 대로 옛 방식은
 * {@code NOT_OWNERSHIP} 이라는 열거 오라클을 만들고 SELECT 를 한 번 더 쳤다.
 *
 * <p>그래서 이 클래스는 이제 <b>어느 조회를 부르는가</b>만 본다. 모드별 실제 동작은
 * {@code ApiKeyOwnershipTest.StrictOwned} 가 목이 아니라 진짜 구현으로 검증한다.
 *
 * <p>{@code ATTACKER} 를 {@code OWNER} 와 다른 값으로 유지한다. 거부 경로에서 요청 계정을
 * 상수로 굳히는 변이를 잡으려면 둘이 달라야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드 summary 접근 통제")
class GetDashboardSummaryUseCaseTest {

    private static final long OWNER = 100L;
    /** 키의 주인이 아닌 제3자. {@code OWNER} 와 반드시 달라야 한다 — 이 테스트의 요점이다. */
    private static final long ATTACKER = 999L;
    private static final long PROJECT = 42L;
    private static final String KEY = "univs_live_abcdefghijklmnop";

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private DashboardStatsService dashboardStatsService;

    @InjectMocks
    private GetDashboardSummaryUseCase getDashboardSummaryUseCase;

    private ApiKey apiKey;
    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder().accountId(OWNER).isDeleted(false).build();
        ReflectionTestUtils.setField(project, "id", PROJECT);

        apiKey = ApiKey.builder().project(project).apiKey(KEY).isActive(true).build();
    }

    @Test
    @DisplayName("키 조회에 요청 계정을 그대로 넘긴다")
    void 요청_계정을_넘긴다() {
        // 이 인자를 0L 같은 상수로 바꾸면 아무 계정이나 남의 키로 대시보드를 볼 수 있다.
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(apiKeyService).findStrictlyOwnedByApiKey(KEY, OWNER);
    }

    /**
     * 느슨한 조회({@code findOwnedByApiKey})를 쓰면 안 된다.
     *
     * <p>그쪽은 {@code gate.security.api-key-ownership.mode = LOG_ONLY} 에서 남의 키를
     * 통과시킨다. 대시보드는 프로젝트 집계를 통째로 내주므로 그 스위치의 영향권 밖에
     * 있어야 한다. 모드별 실제 동작은 {@code ApiKeyOwnershipTest.StrictOwned} 가 진짜
     * 구현으로 검증한다 — 여기서는 <b>어느 쪽을 부르는가</b>만 못박는다.
     */
    @Test
    @DisplayName("모드와 무관하게 막는 조회를 쓴다 — 느슨한 쪽이 아니다")
    void 엄격한_조회를_쓴다() {
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(apiKeyService).findStrictlyOwnedByApiKey(KEY, OWNER);
        verify(apiKeyService, never()).findOwnedByApiKey(any(), any());
    }

    @Test
    @DisplayName("소유 검증이 거부하면 집계를 읽지 않는다")
    void 소유_검증_실패는_전파된다() {
        willThrow(new CustomGateException(ErrorType.API_KEY_NOT_FOUND))
                .given(apiKeyService).findStrictlyOwnedByApiKey(KEY, ATTACKER);

        assertThatThrownBy(() -> getDashboardSummaryUseCase.execute(ATTACKER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(dashboardStatsService, never()).countRegistrations(eq(PROJECT), any(), any());
    }


    /**
     * 집계 10개가 결과의 제자리에 들어가는지 본다.
     *
     * <p>델타 리뷰 지적: 나머지 테스트가 전부 상호작용 검증뿐이라 {@code return null} 이나
     * 10개 인자 순서 뒤바뀜이 전부 통과했다. 열 값을 다 다르게 주면 한 쌍만 바뀌어도 깨진다.
     */
    @Test
    @DisplayName("집계 결과를 자리에 맞게 담는다")
    void 집계_결과를_자리에_맞게_담는다() {
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        given(dashboardStatsService.countRegistrations(eq(PROJECT), any(LocalDateTime.class), eq(FeatureType.FACE))).willReturn(1L);
        given(dashboardStatsService.countTotalRegistrations(PROJECT, FeatureType.FACE)).willReturn(2L);
        given(dashboardStatsService.countVerifyById(eq(PROJECT), any(LocalDateTime.class), eq(FeatureType.FACE))).willReturn(3L);
        given(dashboardStatsService.countTotalVerifyById(PROJECT, FeatureType.FACE)).willReturn(4L);
        given(dashboardStatsService.countVerifyByImage(eq(PROJECT), any(LocalDateTime.class), eq(FeatureType.FACE))).willReturn(5L);
        given(dashboardStatsService.countTotalVerifyByImage(PROJECT, FeatureType.FACE)).willReturn(6L);
        given(dashboardStatsService.countIdentify(eq(PROJECT), any(LocalDateTime.class), eq(FeatureType.FACE))).willReturn(7L);
        given(dashboardStatsService.countTotalIdentify(PROJECT, FeatureType.FACE)).willReturn(8L);
        given(dashboardStatsService.countLiveness(eq(PROJECT), any(LocalDateTime.class), eq(FeatureType.FACE))).willReturn(9L);
        given(dashboardStatsService.countTotalLiveness(PROJECT, FeatureType.FACE)).willReturn(10L);

        DashboardSummaryResult result =
                getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        assertThat(result).isEqualTo(new DashboardSummaryResult(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
    }

    /**
     * 요청 파라미터가 집계까지 그대로 전달되는지 (3차 리뷰 지적).
     *
     * <p>위 테스트가 슬롯 순서는 못박지만 인자는 못박지 못했다. 열 개 스텁이 전부
     * {@code any(LocalDateTime.class)} 라 {@code periodFrom(period)} 를 {@code now()} 로 바꿔도
     * 통과했고, 픽스처가 FACE 뿐이라 {@code featureType} 을 {@code FACE} 상수로 굳혀도 통과했다.
     *
     * <p>둘 다 클라이언트가 보내는 쿼리 파라미터다. 전자가 깨지면 {@code period=MONTH} 요청이
     * 조용히 오늘치만 돌려주고, 후자가 깨지면 PALM 대시보드 타일에 FACE 수치가 뜬다.
     */
    @Test
    @DisplayName("period 와 featureType 을 집계에 그대로 넘긴다")
    void 요청_파라미터를_그대로_넘긴다() {
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.MONTH, FeatureType.PALM);

        LocalDateTime expectedFrom = DashboardStatsService.periodFrom(TrendPeriod.MONTH);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dashboardStatsService)
                .countRegistrations(eq(PROJECT), from.capture(), eq(FeatureType.PALM));

        // periodFrom 은 호출 시각 기준이라 정확히 같을 수 없다. now() 로 바뀌면 한 달 차이가 난다.
        assertThat(from.getValue())
                .as("period 를 무시하고 now() 를 쓰면 이 단언이 깨진다")
                .isCloseTo(expectedFrom, within(5, ChronoUnit.SECONDS));

        // 열 개 전부가 아니라 대표 두 개만 본다 — 인자 통과를 보는 것이지 배선을 다시 세는 게 아니다.
        verify(dashboardStatsService).countTotalLiveness(PROJECT, FeatureType.PALM);
    }
}
