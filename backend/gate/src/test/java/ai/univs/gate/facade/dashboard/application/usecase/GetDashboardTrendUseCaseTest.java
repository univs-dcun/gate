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
 * <p>UG-288 에서 {@code /summary} 에만 모드 무관 검증이 있었고 나머지 대시보드 3종에는 없었다.
 * {@code ApiKeyService.findOwnedByApiKey} 의 소유 검증은
 * {@code gate.security.api-key-ownership.mode = LOG_ONLY} 에서 통과시키므로, 그 스위치를 켜는
 * 순간 이 셋만 남의 집계를 그대로 내주는 상태였다.
 *
 * <p>지금은 네 엔드포인트가 {@code findStrictlyOwnedByApiKey} 로 통일됐다. 이 클래스는
 * <b>어느 조회를 부르는가</b>만 본다 — 모드별 실제 동작은
 * {@code ApiKeyOwnershipTest.StrictOwned} 가 진짜 구현으로 검증한다.
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
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardTrendUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

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

        getDashboardTrendUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(apiKeyService).findStrictlyOwnedByApiKey(KEY, OWNER);
        verify(apiKeyService, never()).findOwnedByApiKey(any(), any());
    }

    @Test
    @DisplayName("소유 검증이 거부하면 집계를 읽지 않는다")
    void 소유_검증_실패는_전파된다() {
        willThrow(new CustomGateException(ErrorType.API_KEY_NOT_FOUND))
                .given(apiKeyService).findStrictlyOwnedByApiKey(KEY, ATTACKER);

        assertThatThrownBy(() -> getDashboardTrendUseCase.execute(ATTACKER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

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
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

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
