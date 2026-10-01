package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.support.webhook.WebhookSecrets;
import ai.univs.gate.support.webhook.WebhookTargetPolicy;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpsertWebhookConfigUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookTargetPolicy webhookTargetPolicy;

    @Transactional
    public WebhookConfigResult execute(UpsertWebhookConfigInput input) {
        Project project = projectService.validateOwnership(input.projectId(), input.accountId());
        // UG-111: 내부 주소·http(s) 아닌 주소는 저장하지 않는다. 보낼 때도 다시 검사하지만,
        // 여기서 막아야 화면이 바로 알려 준다 — 보낼 때 막히면 로그에만 남는다.
        webhookTargetPolicy.validate(input.webhookUrl());

        // UG-344: 행을 잠근다. 이 트랜잭션은 키 컬럼까지 통째로 다시 쓰므로, 그사이 전송 쪽이 채운 키나 다른 탭의
        // 재발급을 읽은 값으로 덮어쓰면 수신 측이 가진 키가 말없이 무효가 된다.
        Optional<WebhookConfig> existing = webhookConfigRepository.findForUpdateByProjectId(input.projectId());

        WebhookConfig config;
        if (existing.isEmpty()) {
            config = WebhookConfig.builder()
                    .project(project)
                    .webhookUrl(input.webhookUrl())
                    .demoEnabled(input.demoEnabled())
                    .apiEnabled(input.apiEnabled())
                    .webhookSecret(WebhookSecrets.generate())
                    .build();
            webhookConfigRepository.save(config);
            log.info("Webhook config created: projectId={}", input.projectId());
        } else {
            config = existing.get();
            config.update(
                    input.webhookUrl(),
                    input.demoEnabled(),
                    input.apiEnabled());
            // UG-344 이전에 만든 설정은 키가 없다
            config.assignSecretIfAbsent(WebhookSecrets.generate());
            log.info("Webhook config updated: projectId={}", input.projectId());
        }

        return WebhookConfigResult.from(config, LocalDateTime.now(ZoneOffset.UTC));
    }
}
