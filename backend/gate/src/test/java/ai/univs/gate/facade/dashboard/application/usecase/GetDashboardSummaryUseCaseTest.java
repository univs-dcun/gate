package ai.univs.gate.facade.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import ai.univs.gate.support.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대시보드 summary 의 접근 통제 (UG-281, UG-288).
 *
 * <p>이 UseCase 에는 테스트가 하나도 없었다. UG-288 반박 리뷰가 변이 하나로 그것을 드러냈다 —
 * {@code findOwnedByApiKey(apiKey, accountId)} 를 {@code (apiKey, 0L)} 로 바꿔도 전 테스트가
 * 초록이었다. 즉 테넌트 격리가 통째로 사라져도 아무도 몰랐다.
 *
 * <p>특히 {@code projectService.validateOwnership} 호출은 지우기 쉬워 보이는 중복이다. UG-288
 * 작업에서 실제로 지웠다가 되돌렸다 — {@code ApiKeyService} 쪽 소유 검증은
 * {@code gate.security.api-key-ownership.mode = LOG_ONLY} 에서 통과시키는 반면 이쪽은 항상 막기
 * 때문이다. 두 검사는 등가가 아니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("대시보드 summary 접근 통제")
class GetDashboardSummaryUseCaseTest {

    private static final long OWNER = 100L;
    private static final long PROJECT = 42L;
    private static final String KEY = "univs_live_abcdefghijklmnop";

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ProjectService projectService;

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
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        verify(apiKeyService).findOwnedByApiKey(KEY, OWNER);
    }

    @Test
    @DisplayName("프로젝트 소유 검증을 별도로 한 번 더 한다 — LOG_ONLY 에서도 막기 위해서다")
    void 프로젝트_소유_검증을_거친다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);

        getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE);

        // ProjectService.validateOwnership 은 모드와 무관하게 항상 던진다. 이 호출이 사라지면
        // LOG_ONLY 로 되돌리는 순간 남의 집계가 그대로 나간다.
        verify(projectService).validateOwnership(PROJECT, OWNER);
    }

    @Test
    @DisplayName("소유 검증이 거부하면 집계를 읽지 않는다")
    void 소유_검증_실패는_전파된다() {
        given(apiKeyService.findOwnedByApiKey(KEY, OWNER)).willReturn(apiKey);
        willThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP))
                .given(projectService).validateOwnership(PROJECT, OWNER);

        assertThatThrownBy(() ->
                getDashboardSummaryUseCase.execute(OWNER, KEY, TrendPeriod.WEEK, FeatureType.FACE))
                .isInstanceOf(CustomGateException.class);

        verify(dashboardStatsService, org.mockito.Mockito.never())
                .countRegistrations(eq(PROJECT), any(), any());
    }
}
