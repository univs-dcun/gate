package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetDashboardTrendUseCase {

    private final ApiKeyService apiKeyService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardTrendResult execute(String apiKey, TrendPeriod period) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        long projectId = findApiKey.getProject().getId();
        return dashboardStatsService.getTrend(projectId, period);
    }
}
