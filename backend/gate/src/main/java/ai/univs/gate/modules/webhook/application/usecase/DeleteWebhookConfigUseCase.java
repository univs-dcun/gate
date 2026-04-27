package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteWebhookConfigUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;

    @Transactional
    public void execute(Long projectId) {
        UserContext ctx = UserContext.get();
        projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());

        webhookConfigRepository.findByProjectId(projectId).ifPresent(config -> {
            webhookConfigRepository.delete(config);
            log.info("Webhook config deleted: projectId={}", projectId);
        });
    }
}
