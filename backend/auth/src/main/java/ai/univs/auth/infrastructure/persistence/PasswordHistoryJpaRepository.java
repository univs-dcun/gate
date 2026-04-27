package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordHistoryJpaRepository extends JpaRepository<PasswordHistory, Long> {

    Optional<PasswordHistory> findTop1ByAccountIdOrderByChangedAtDesc(Long accountId);
}
