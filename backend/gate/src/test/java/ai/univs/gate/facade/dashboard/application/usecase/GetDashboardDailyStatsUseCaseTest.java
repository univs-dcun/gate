package ai.univs.gate.facade.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
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
 * 대시보드 daily 의 접근 통제 (UG-301).
 *
 * <p>배경은 {@link GetDashboardTrendUseCaseTest} 주석 참고. 여기는 페이징 파라미터가 두 개라
 * {@code page} 와 {@code pageSize} 가 뒤바뀌는 변이까지 함께 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드 daily 접근 통제")
class GetDashboardDailyStatsUseCaseTest {

    private static final long OWNER = 100L;
    /** 키의 주인이 아닌 제3자. {@code OWNER} 와 반드시 달라야 한다. */
    private static final long ATTACKER = 999L;
    private static final long PROJECT = 42L;
    private static final String KEY = "univs_live_abcdefghijklmnop";
    /** page 와 pageSize 를 서로 다르게 둔다 — 같으면 자리 바뀜을 못 잡는다. */
    private static final int PAGE = 2;
    private static final int PAGE_SIZE = 30;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private DashboardStatsService dashboardStatsService;

    @InjectMocks
    private GetDashboardDailyStatsUseCase getDashboardDailyStatsUseCase;

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

        getDashboardDailyStatsUseCase.execute(OWNER, KEY, PAGE, PAGE_SIZE, FeatureType.FACE);

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

        getDashboardDailyStatsUseCase.execute(OWNER, KEY, PAGE, PAGE_SIZE, FeatureType.FACE);

        verify(apiKeyService).findStrictlyOwnedByApiKey(KEY, OWNER);
        verify(apiKeyService, never()).findOwnedByApiKey(any(), any());
    }

    @Test
    @DisplayName("소유 검증이 거부하면 집계를 읽지 않는다")
    void 소유_검증_실패는_전파된다() {
        willThrow(new CustomGateException(ErrorType.API_KEY_NOT_FOUND))
                .given(apiKeyService).findStrictlyOwnedByApiKey(KEY, ATTACKER);

        assertThatThrownBy(() -> getDashboardDailyStatsUseCase.execute(ATTACKER, KEY, PAGE, PAGE_SIZE, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(dashboardStatsService, never()).getDailyStats(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("page·pageSize·featureType 을 그대로 넘기고 결과를 그대로 돌려준다")
    void 요청_파라미터를_그대로_넘긴다() {
        given(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        DashboardDailyStatsResult expected =
                new DashboardDailyStatsResult(
                        List.of(), new CustomPageResult(PAGE_SIZE, PAGE, 0L, 0, 0L));
        given(dashboardStatsService.getDailyStats(PROJECT, PAGE, PAGE_SIZE, FeatureType.PALM))
                .willReturn(expected);

        DashboardDailyStatsResult actual =
                getDashboardDailyStatsUseCase.execute(OWNER, KEY, PAGE, PAGE_SIZE, FeatureType.PALM);

        assertThat(actual).isSameAs(expected);
    }
}
