package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.dashboard.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

    private final ApiKeyService apiKeyService;
    private final DashboardStatsService dashboardStatsService;

    public DashboardSummaryResult execute(Long accountId, String apiKey, TrendPeriod period, FeatureType featureType) {
        // UG-288: 여기 있던 projectService.validateOwnership 호출을 지웠다. UG-281 이 소유 검증을
        // findOwnedByApiKey 로 옮긴 뒤에도 '삭제되지 않은 프로젝트인가' 하나 때문에 남겨 뒀던
        // 것인데, 그 검사가 이제 ApiKeyService 의 키 조회 안으로 들어갔다. 대시보드 4종 중 이
        // UseCase 만 검사를 갖고 있어 삭제된 프로젝트에서 summary 만 다르게 응답하던 불일치도
        // 함께 사라진다 (실제로는 delete() 가 플래그를 세우지 않아 그 차이조차 나지 않았다).
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(apiKey, accountId);
        Long projectId = findApiKey.getProject().getId();
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
