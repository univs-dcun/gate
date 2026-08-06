package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardSummaryResult execute(Long accountId, String apiKey, TrendPeriod period, FeatureType featureType) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();

        // UG-288: 이 호출을 지우려다 되돌렸다. 반박 리뷰가 두 검사가 등가가 아님을 짚었다.
        //
        // UG-281 이 소유 검증을 findOwnedByApiKey 로 옮긴 뒤 여기 남은 이유는 두 가지였는데,
        // 그중 '삭제되지 않은 프로젝트인가' 는 이제 ApiKeyService 의 키 조회가 담당한다.
        // 남은 하나가 문제다 — ProjectService.validateOwnership 은 소유 불일치에 **항상**
        // NOT_OWNERSHIP 을 던지지만, findOwnedByApiKey 쪽 소유 검증은
        // gate.security.api-key-ownership.mode 가 LOG_ONLY 면 통과시킨다.
        //
        // 즉 이 줄을 지우면 LOG_ONLY 로 되돌리는 순간 이 엔드포인트가 남의 대시보드 집계를
        // 그대로 내주게 된다. LOG_ONLY 는 UG-281 의 비상 되돌림 수단이므로, 그걸 쓰는 상황에서
        // 폭발 반경이 넓어지는 것은 받아들일 수 없다.
        //
        // 나머지 대시보드 3종에는 이 검사가 없어 LOG_ONLY 에서 열린다. 그건 UG-281 이 남긴
        // 기존 상태이고, 이 티켓에서 함께 손대면 범위가 섞인다. 별건으로 분리한다.
        projectService.validateOwnership(project.getId(), accountId);

        Long projectId = project.getId();
        LocalDateTime from = DashboardStatsService.periodFrom(period);
        return new DashboardSummaryResult(
                dashboardStatsService.countRegistrations(projectId, from, featureType),
                dashboardStatsService.countTotalRegistrations(projectId, featureType),
                dashboardStatsService.countVerifyById(projectId, from, featureType),
                dashboardStatsService.countTotalVerifyById(projectId, featureType),
                dashboardStatsService.countVerifyByImage(projectId, from, featureType),
                dashboardStatsService.countTotalVerifyByImage(projectId, featureType),
                dashboardStatsService.countIdentify(projectId, from, featureType),
                dashboardStatsService.countTotalIdentify(projectId, featureType),
                dashboardStatsService.countLiveness(projectId, from, featureType),
                dashboardStatsService.countTotalLiveness(projectId, featureType)
        );
    }
}
