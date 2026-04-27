package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetWebhookConfigUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;

    @Transactional(readOnly = true)
    public WebhookConfigResult execute(Long projectId) {
        UserContext ctx = UserContext.get();
        projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());

        return webhookConfigRepository.findByProjectId(projectId)
                .map(WebhookConfigResult::from)
                .orElse(null);
    }
}
