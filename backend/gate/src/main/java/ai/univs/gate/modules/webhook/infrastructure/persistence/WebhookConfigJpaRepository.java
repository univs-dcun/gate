package ai.univs.gate.modules.webhook.infrastructure.persistence;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookConfigJpaRepository extends JpaRepository<WebhookConfig, Long> {

    Optional<WebhookConfig> findByProjectId(Long projectId);
}
