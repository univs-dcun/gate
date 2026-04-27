package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectService projectService;
    private final BillingClient billingClient;

    @Transactional
    public void execute(Long accountId, Long projectId) {
        Project project = projectService.validateOwnership(projectId, accountId);

        // 유료 플랜인 경우 삭제 불가 (관리자 문의 필요)
        billingClient.handleProjectDeletion(projectId);

        project.delete();
    }
}
