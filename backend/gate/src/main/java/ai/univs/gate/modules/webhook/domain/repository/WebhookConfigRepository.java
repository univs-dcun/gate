package ai.univs.gate.modules.webhook.domain.repository;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;

import java.util.Optional;

public interface WebhookConfigRepository {

    WebhookConfig save(WebhookConfig config);

    Optional<WebhookConfig> findByProjectId(Long projectId);

    /** 행을 잠그고 읽는다 — 저장·재발급처럼 키 컬럼을 함께 쓰는 경로용 (UG-344). 트랜잭션 안에서 부른다. */
    Optional<WebhookConfig> findForUpdateByProjectId(Long projectId);

    /**
     * 키가 비어 있으면 채운다 (UG-344). 조건부 갱신이라 동시에 불려도 먼저 온 하나만 들어간다.
     *
     * @return 이번에 채웠으면 true
     */
    boolean assignSecretIfAbsent(Long webhookConfigId, String secret);

    void delete(WebhookConfig config);
}
