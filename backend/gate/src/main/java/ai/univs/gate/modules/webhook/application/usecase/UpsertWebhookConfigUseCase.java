package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpsertWebhookConfigUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;

    @Transactional
    public WebhookConfigResult execute(UpsertWebhookConfigInput input) {
        Project project = projectService.validateOwnership(input.projectId(), input.accountId());

        Optional<WebhookConfig> existing = webhookConfigRepository.findByProjectId(input.projectId());

        WebhookConfig config;
        if (existing.isEmpty()) {
            config = WebhookConfig.builder()
                    .project(project)
                    .webhookUrl(input.webhookUrl())
                    .demoEnabled(input.demoEnabled())
                    .sdkEnabled(input.sdkEnabled())
                    .apiEnabled(input.apiEnabled())
                    .build();
            webhookConfigRepository.save(config);
            log.info("Webhook config created: projectId={}", input.projectId());
        } else {
            config = existing.get();
            config.update(
                    input.webhookUrl(),
                    input.demoEnabled(),
                    input.sdkEnabled(),
                    input.apiEnabled());
            log.info("Webhook config updated: projectId={}", input.projectId());
        }

        return WebhookConfigResult.from(config);
    }
}
