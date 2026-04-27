package ai.univs.gate.modules.webhook.domain.repository;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;

import java.util.Optional;

public interface WebhookConfigRepository {

    WebhookConfig save(WebhookConfig config);

    Optional<WebhookConfig> findByProjectId(Long projectId);

    void delete(WebhookConfig config);
}
