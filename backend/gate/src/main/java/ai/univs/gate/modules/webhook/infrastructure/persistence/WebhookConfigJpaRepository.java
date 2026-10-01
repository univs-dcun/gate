package ai.univs.gate.modules.webhook.infrastructure.persistence;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WebhookConfigJpaRepository extends JpaRepository<WebhookConfig, Long> {

    Optional<WebhookConfig> findByProjectId(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WebhookConfig> findForUpdateByProjectId(Long projectId);

    /**
     * 키가 비어 있을 때만 채운다 (UG-344). {@code WHERE webhook_secret IS NULL} 이 경쟁을 정한다 — 늦게 온 쪽은
     * 0행이다. 시스템이 채우는 값이라 감사 컬럼(updated_at·updated_by)은 건드리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WebhookConfig c SET c.webhookSecret = :secret WHERE c.id = :id AND c.webhookSecret IS NULL")
    int assignSecretIfAbsent(@Param("id") Long id, @Param("secret") String secret);
}
