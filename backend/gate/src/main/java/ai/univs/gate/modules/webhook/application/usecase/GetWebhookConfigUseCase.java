package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookSecrets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetWebhookConfigUseCase {

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;

    /**
     * 읽기 전용이 아니다 (UG-344) — UG-344 이전에 만든 설정은 키가 없어서, 처음 조회할 때 채운다. 조건부 갱신이라
     * 전송 쪽이 동시에 채워도 먼저 들어간 키 하나로 정해지고, 다시 읽어 그 키를 보여 준다.
     */
    @Transactional
    public WebhookConfigResult execute(Long projectId) {
        UserContext ctx = UserContext.get();
        projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());

        Optional<WebhookConfig> config = webhookConfigRepository.findByProjectId(projectId);
        if (config.isPresent() && config.get().getWebhookSecret() == null) {
            webhookConfigRepository.assignSecretIfAbsent(config.get().getId(), WebhookSecrets.generate());
            config = webhookConfigRepository.findByProjectId(projectId);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return config.map(c -> WebhookConfigResult.from(c, now)).orElse(null);
    }
}
