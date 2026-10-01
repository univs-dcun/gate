package ai.univs.gate.modules.webhook.infrastructure.persistence;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    public Optional<WebhookConfig> findForUpdateByProjectId(Long projectId) {
        return jpaRepository.findForUpdateByProjectId(projectId);
    }

    /**
     * 웹훅 전송 스레드에서도 불린다 — 바깥 트랜잭션이 없을 수 있어 여기서 연다. 있으면 그 안에서 돈다.
     */
    @Override
    @Transactional
    public boolean assignSecretIfAbsent(Long webhookConfigId, String secret) {
        return jpaRepository.assignSecretIfAbsent(webhookConfigId, secret) == 1;
    }

    @Override
    public void delete(WebhookConfig config) {
        jpaRepository.delete(config);
    }
}
