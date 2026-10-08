package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookToggleCache;
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
    private final WebhookToggleCache webhookToggleCache;

    @Transactional
    public void execute(Long projectId) {
        UserContext ctx = UserContext.get();
        projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());

        webhookConfigRepository.findByProjectId(projectId).ifPresent(config -> {
            webhookConfigRepository.delete(config);
            // 전송 쪽이 기억한 토글을 커밋 뒤에 지운다 — 남아 있어도 전송 스레드가 DB 로 다시 보지만 대기열을 쓴다 (UG-361)
            webhookToggleCache.evictAfterCommit(projectId);
            log.info("Webhook config deleted: projectId={}", projectId);
        });
    }
}
