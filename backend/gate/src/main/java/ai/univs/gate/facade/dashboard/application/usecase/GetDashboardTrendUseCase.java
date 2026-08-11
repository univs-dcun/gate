package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardTrendResult;
import ai.univs.gate.facade.dashboard.domain.enums.TrendPeriod;
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
public class GetDashboardTrendUseCase {

    private final ApiKeyService apiKeyService;
    private final DashboardStatsService dashboardStatsService;

    @Transactional(readOnly = true)
    public DashboardTrendResult execute(Long accountId, String apiKey, TrendPeriod period, FeatureType featureType) {
        // UG-301: 모드와 무관하게 막는 조회다. 일반 findOwnedByApiKey 는
        // gate.security.api-key-ownership.mode = LOG_ONLY 에서 통과시키는데, 그 스위치를
        // 켜는 순간 이 엔드포인트가 남의 프로젝트 집계를 통째로 내주게 된다.
        // 사유와 한계(나머지 16곳은 아직 열려 있다)는 ApiKeyService 쪽 주석 참고.
        ApiKey findApiKey = apiKeyService.findStrictlyOwnedByApiKey(apiKey, accountId);
        Project project = findApiKey.getProject();


        return dashboardStatsService.getTrend(project.getId(), period, featureType);
    }
}
