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

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final DashboardStatsService dashboardStatsService;

    public DashboardSummaryResult execute(Long accountId, String apiKey, TrendPeriod period, FeatureType featureType) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();
        // UG-281: 소유 검증은 findOwnedByApiKey 로 옮겼다. 이 호출을 남겨 둔 것은 소유가 아니라
        // '삭제되지 않은 프로젝트인가'(findByIdAndIsDeletedFalse) 때문이다. 프로젝트를 소프트
        // 삭제해도 api_key.is_active 는 그대로라(DeleteProjectUseCase 는 project.delete() 만
        // 호출한다) 키가 계속 유효하고, 이 검사만 그것을 막고 있다. 나머지 대시보드 3종과
        // 특징점·이력 경로에는 이 검사가 없어 삭제된 프로젝트도 조회된다 — 별건으로 분리한다.
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
