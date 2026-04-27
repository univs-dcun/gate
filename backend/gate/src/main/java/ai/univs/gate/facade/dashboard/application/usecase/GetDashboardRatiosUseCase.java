package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetDashboardRatiosUseCase {

    private final ApiKeyService apiKeyService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardRatiosResult execute(String apiKey) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        long projectId = findApiKey.getProject().getId();
        return dashboardStatsService.getRatios(projectId);
    }
}
