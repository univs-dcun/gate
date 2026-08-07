package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
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
public class GetDashboardDailyStatsUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardDailyStatsResult execute(Long accountId, String apiKey, int page, int pageSize, FeatureType featureType) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();

        // UG-301: LOG_ONLY 에서도 막기 위한 두 번째 검사. 사유는 GetDashboardTrendUseCase 주석 참고.
        projectService.validateOwnership(project.getId(), accountId);

        return dashboardStatsService.getDailyStats(project.getId(), page, pageSize, featureType);
    }
}
