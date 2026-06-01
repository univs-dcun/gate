package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardRatiosResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GetDashboardRatiosUseCase {

    private final ApiKeyService apiKeyService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardRatiosResult execute(String apiKey, TrendPeriod period) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        long projectId = findApiKey.getProject().getId();
        LocalDateTime from = DashboardStatsService.periodFrom(period);
        return dashboardStatsService.getRatios(projectId, from);
    }
}
