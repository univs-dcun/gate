package ai.univs.gate.modules.webhook.infrastructure.persistence;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WebhookConfigRepositoryImpl implements WebhookConfigRepository {

    private final WebhookConfigJpaRepository jpaRepository;

    @Override
    public WebhookConfig save(WebhookConfig config) {
        return jpaRepository.save(config);
    }

    @Override
    public Optional<WebhookConfig> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(WebhookConfig config) {
        jpaRepository.delete(config);
    }
}
