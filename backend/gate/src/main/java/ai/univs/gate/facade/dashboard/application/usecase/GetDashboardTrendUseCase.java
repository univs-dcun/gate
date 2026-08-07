package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
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

@Component
@RequiredArgsConstructor
public class GetDashboardTrendUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardTrendResult execute(Long accountId, String apiKey, TrendPeriod period, FeatureType featureType) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();

        // UG-301: summary 와 같은 이유로 소유 검증을 한 번 더 한다.
        //
        // findOwnedByApiKey 의 소유 검증은 gate.security.api-key-ownership.mode 가 LOG_ONLY 면
        // 경고만 남기고 통과시킨다. ProjectService.validateOwnership 은 모드와 무관하게 항상
        // 던진다. 두 검사는 등가가 아니다.
        //
        // UG-288 에서 summary 에만 이 줄이 있었고 나머지 대시보드 3종에는 없었다 — LOG_ONLY 로
        // 되돌리는 순간 이 세 엔드포인트만 남의 집계를 그대로 내주게 된다. LOG_ONLY 는 UG-281 의
        // 비상 되돌림 수단이므로 그걸 쓰는 상황에서 폭발 반경이 넓어지는 것은 받아들일 수 없다.
        projectService.validateOwnership(project.getId(), accountId);

        return dashboardStatsService.getTrend(project.getId(), period, featureType);
    }
}
