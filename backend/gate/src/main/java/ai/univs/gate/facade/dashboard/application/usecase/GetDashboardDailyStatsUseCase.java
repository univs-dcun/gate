package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetDashboardDailyStatsUseCase {

    private final ApiKeyService apiKeyService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardDailyStatsResult execute(Long accountId, String apiKey, int page, int pageSize, FeatureType featureType) {
        // UG-301 은 이 자리에 '모드와 무관하게 막는' 별도 조회를 뒀었다. LOG_ONLY 스위치가
        // 일반 조회를 통과시켰기 때문인데, 그 스위치가 UG-306 에서 사라져 두 조회가 같아졌다.
        // 이제 findOwnedByApiKey 하나로 충분하다.
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();


        return dashboardStatsService.getDailyStats(project.getId(), page, pageSize, featureType);
    }
}
