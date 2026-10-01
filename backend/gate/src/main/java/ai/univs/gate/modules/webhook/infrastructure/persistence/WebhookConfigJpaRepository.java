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

    /**
     * 행을 잠그고 읽는다 (UG-344). <b>파생 쿼리로 바꾸지 말 것</b> — {@code findForUpdateByProjectId} 파생 쿼리는
     * {@code project} 연관을 LEFT JOIN 으로 렌더링하고, PostgreSQL 방언은 외부 조인에 FOR UPDATE 를 걸지 못해
     * Hibernate 가 "잠금 없이 읽고 → id 로 따로 잠그는" follow-on locking 으로 바꾼다. 잠근 뒤 다시 읽지 않으므로
     * 엔티티가 낡은 키를 들고 있다가 통째로 되써 동시 재발급을 말없이 지운다 (반박 리뷰 B1, H2 방언에서는 안 보인다).
     * {@code WebhookConfigLockSqlSliceTest} 가 PostgreSQL 방언으로 렌더링한 SQL 을 지킨다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM WebhookConfig c WHERE c.project.id = :projectId")
    Optional<WebhookConfig> findForUpdateByProjectId(@Param("projectId") Long projectId);

    /**
     * 키가 비어 있을 때만 채운다 (UG-344). {@code WHERE webhook_secret IS NULL} 이 경쟁을 정한다 — 늦게 온 쪽은
     * 0행이다. 시스템이 채우는 값이라 감사 컬럼(updated_at·updated_by)은 건드리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WebhookConfig c SET c.webhookSecret = :secret WHERE c.id = :id AND c.webhookSecret IS NULL")
    int assignSecretIfAbsent(@Param("id") Long id, @Param("secret") String secret);
}
