package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final DashboardStatsService dashboardStatsService;

    public DashboardSummaryResult execute(String apiKey, TrendPeriod period) {
        UserContext ctx = UserContext.get();

        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();
        projectService.validateOwnership(project.getId(), ctx.getAccountIdAsLong());

        Long projectId = project.getId();
        LocalDateTime from = DashboardStatsService.periodFrom(period);
        return new DashboardSummaryResult(
                dashboardStatsService.countRegistrations(projectId, from),
                dashboardStatsService.countVerifyById(projectId, from),
                dashboardStatsService.countVerifyByImage(projectId, from),
                dashboardStatsService.countIdentify(projectId, from),
                dashboardStatsService.countLiveness(projectId, from)
        );
    }
}
