package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.facade.dashboard.application.result.DashboardSummaryResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.billing.client.dto.BillingSummaryFeignResponseDTO;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final BillingClient billingClient;

    public DashboardSummaryResult execute(String apiKey) {
        UserContext ctx = UserContext.get();

        // 프로젝트 소유자 체크
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();
        projectService.validateOwnership(project.getId(), ctx.getAccountIdAsLong());

        BillingSummaryFeignResponseDTO summary =
                billingClient.getSubscriptionSummary(project.getId());

        return new DashboardSummaryResult(
                // DB used, DB allocated, DB limit
                summary.getDbUsedCount(),
                summary.getDbStorageLimit(),
                summary.getDbStorageLimit() - summary.getDbUsedCount(),

                // Verify used, Verify allocated, Verify limit
                summary.getVerifyAllocated() - summary.getVerifyLimit(),
                summary.getVerifyAllocated(),
                summary.getVerifyLimit(),

                // Identify used, Identify allocated, Identify limit
                summary.getIdentifyAllocated() - summary.getIdentifyLimit(),
                summary.getIdentifyAllocated(),
                summary.getIdentifyLimit(),

                // Liveness used, Liveness allocated, Liveness limit
                summary.getLivenessAllocated() - summary.getLivenessLimit(),
                summary.getLivenessAllocated(),
                summary.getLivenessLimit()
        );
    }
}
