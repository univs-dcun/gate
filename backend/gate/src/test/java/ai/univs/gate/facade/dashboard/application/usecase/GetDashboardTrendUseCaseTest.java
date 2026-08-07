package ai.univs.gate.facade.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import ai.univs.gate.support.project.ProjectService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대시보드 trend 의 접근 통제 (UG-301).
 *
 * <p>UG-288 에서 {@code /summary} 에만 {@code projectService.validateOwnership} 이 있었고 나머지
 * 대시보드 3종에는 없었다. {@code ApiKeyService} 쪽 소유 검증은
 * {@code gate.security.api-key-ownership.mode = LOG_ONLY} 에서 통과시키므로, 그 스위치를 켜는
 * 순간 이 셋만 남의 집계를 그대로 내주는 상태였다.
 *
 * <p>{@code GetDashboardSummaryUseCaseTest} 와 같은 구성이다. 특히
 * <b>요청 계정과 프로젝트 소유자를 다른 값으로 둔다</b> — 둘이 같으면
 * {@code validateOwnership(id, accountId)} 를 {@code validateOwnership(id, project.getAccountId())}
 * 로 바꿔도 전 테스트가 초록이라 "요청자 기준으로 검증한다" 는 핵심 주장이 검증되지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드 trend 접근 통제")
class GetDashboardTrendUseCaseTest {

    private static final long OWNER = 100L;
    /** 키의 주인이 아닌 제3자. {@code OWNER} 와 반드시 달라야 한다. */
    private static final long ATTACKER = 999L;
    private static final long PROJECT = 42L;
    private static final String KEY = "univs_live_abcdefghijklmnop";

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ProjectService projectService;

    @Mock
    private DashboardStatsService dashboardStatsService;

    @InjectMocks
    private GetDashboardTrendUseCase getDashboardTrendUseCase;

    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().accountId(OWNER).isDeleted(false).build();
        ReflectionTestUtils.setField(project, "id", PROJECT);

        apiKey = ApiKey.builder().project(project).apiKey(KEY).isActive(true).build();
    }

    @Test
    @DisplayName("키 조회에 요청 계정을 그대로 넘긴다")
    void 요청_계정을_넘긴다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardTrendUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(apiKeyService).findOwnedByApiKey(KEY, OWNER);
    }

    @Test
    @DisplayName("프로젝트 소유 검증을 별도로 한 번 더 한다 — LOG_ONLY 에서도 막기 위해서다")
    void 프로젝트_소유_검증을_거친다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardTrendUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(projectService).validateOwnership(PROJECT, OWNER);
    }

    @Test
    @DisplayName("소유 검증이 거부하면 집계를 읽지 않는다")
    void 소유_검증_실패는_전파된다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);
        willThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP))
                .given(projectService).validateOwnership(PROJECT, OWNER);

        assertThatThrownBy(() ->
                getDashboardTrendUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(dashboardStatsService, never()).getTrend(anyLong(), any(), any());
    }

    /**
     * LOG_ONLY 로 되돌린 상황을 그대로 재현한다.
     *
     * <p>{@code findOwnedByApiKey} 가 남의 키를 통과시킨다 — 그게 LOG_ONLY 의 정의다. 그다음 줄의
     * {@code validateOwnership} 이 요청자({@code ATTACKER}) 기준으로 막아야 한다. 키의 주인
     * ({@code OWNER}) 기준으로 검증하면 통과하므로, 그렇게 바뀌면 이 테스트가 깨진다.
     */
    @Test
    @DisplayName("LOG_ONLY 로 남의 키가 통과해도 요청자 기준 소유 검증이 막는다")
    void LOG_ONLY_에서도_요청자_기준으로_막는다() {
        given(apiKeyService.findOwnedByApiKey(KEY, ATTACKER)).willReturn(apiKey);
        willThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP))
                .given(projectService).validateOwnership(PROJECT, ATTACKER);

        assertThatThrownBy(() ->
                getDashboardTrendUseCase.execute(ATTACKER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(projectService).validateOwnership(PROJECT, ATTACKER);
        verify(dashboardStatsService, never()).getTrend(anyLong(), any(), any());
    }

    /**
     * 요청 파라미터가 집계까지 그대로 전달되고, 결과가 가공 없이 반환되는지 본다.
     *
     * <p>상호작용 검증만 있으면 {@code return null} 이나 {@code featureType} 상수화가 전부
     * 통과한다. 픽스처를 {@code MONTH}/{@code PALM} 으로 둬서 기본값으로 굳히는 변이도 잡는다.
     */
    @Test
    @DisplayName("period 와 featureType 을 그대로 넘기고 결과를 그대로 돌려준다")
    void 요청_파라미터를_그대로_넘긴다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        DashboardTrendResult expected = new DashboardTrendResult(
                TrendPeriod.MONTH, List.of("2026-08-01"),
                List.of(1L), List.of(2L), List.of(3L), List.of(4L), List.of(5L));
        given(dashboardStatsService.getTrend(PROJECT, TrendPeriod.MONTH, FeatureType.PALM))
                .willReturn(expected);

        DashboardTrendResult actual =
                getDashboardTrendUseCase.execute(OWNER, KEY, TrendPeriod.MONTH, FeatureType.PALM);

        assertThat(actual).isSameAs(expected);
    }
}
