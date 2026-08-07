package ai.univs.gate.facade.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import ai.univs.gate.support.project.ProjectService;
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
 * 대시보드 ratios 의 접근 통제 (UG-301).
 *
 * <p>배경은 {@link GetDashboardTrendUseCaseTest} 주석 참고. 여기는 {@code period} 가
 * {@code periodFrom} 을 거쳐 {@code LocalDateTime} 으로 바뀌므로 그 변환까지 함께 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드 ratios 접근 통제")
class GetDashboardRatiosUseCaseTest {

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
    private GetDashboardRatiosUseCase getDashboardRatiosUseCase;

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

        getDashboardRatiosUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(apiKeyService).findOwnedByApiKey(KEY, OWNER);
    }

    @Test
    @DisplayName("프로젝트 소유 검증을 별도로 한 번 더 한다 — LOG_ONLY 에서도 막기 위해서다")
    void 프로젝트_소유_검증을_거친다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardRatiosUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(projectService).validateOwnership(PROJECT, OWNER);
    }

    @Test
    @DisplayName("소유 검증이 거부하면 집계를 읽지 않는다")
    void 소유_검증_실패는_전파된다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);
        willThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP))
                .given(projectService).validateOwnership(PROJECT, OWNER);

        assertThatThrownBy(() ->
                getDashboardRatiosUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(dashboardStatsService, never()).getRatios(anyLong(), any(), any());
    }

    /** LOG_ONLY 재현. 상세는 {@link GetDashboardTrendUseCaseTest} 의 같은 이름 테스트 참고. */
    @Test
    @DisplayName("LOG_ONLY 로 남의 키가 통과해도 요청자 기준 소유 검증이 막는다")
    void LOG_ONLY_에서도_요청자_기준으로_막는다() {
        given(apiKeyService.findOwnedByApiKey(KEY, ATTACKER)).willReturn(apiKey);
        willThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP))
                .given(projectService).validateOwnership(PROJECT, ATTACKER);

        assertThatThrownBy(() ->
                getDashboardRatiosUseCase.execute(ATTACKER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(projectService).validateOwnership(PROJECT, ATTACKER);
        verify(dashboardStatsService, never()).getRatios(anyLong(), any(), any());
    }

    @Test
    @DisplayName("period 와 featureType 을 그대로 넘기고 결과를 그대로 돌려준다")
    void 요청_파라미터를_그대로_넘긴다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        DashboardRatiosResult expected = new DashboardRatiosResult(
                new DashboardRatiosResult.RatioItem(1L, 2L),
                new DashboardRatiosResult.RatioItem(3L, 4L),
                new DashboardRatiosResult.RatioItem(5L, 6L),
                new DashboardRatiosResult.RatioItem(7L, 8L),
                new DashboardRatiosResult.RatioItem(9L, 10L));
        given(dashboardStatsService.getRatios(
                eq(PROJECT), any(LocalDateTime.class), eq(FeatureType.PALM))).willReturn(expected);

        DashboardRatiosResult actual =
                getDashboardRatiosUseCase.execute(OWNER, KEY, TrendPeriod.MONTH, FeatureType.PALM);

        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dashboardStatsService).getRatios(eq(PROJECT), from.capture(), eq(FeatureType.PALM));

        // periodFrom 은 호출 시각 기준이라 정확히 같을 수 없다. period 를 무시하고 now() 를 쓰면
        // 한 달 차이가 나므로 이 단언이 깨진다.
        assertThat(from.getValue())
                .isCloseTo(DashboardStatsService.periodFrom(TrendPeriod.MONTH),
                        within(5, ChronoUnit.SECONDS));
    }
}
