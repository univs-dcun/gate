package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardDailyStatsResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
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
    public DashboardDailyStatsResult execute(String apiKey, int page, int pageSize) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        long projectId = findApiKey.getProject().getId();
        return dashboardStatsService.getDailyStats(projectId, page, pageSize);
    }
}
