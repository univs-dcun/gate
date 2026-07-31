package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.PasswordHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryJpaRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findByAccountIdOrderByChangedAtDesc(Long accountId, Pageable pageable);
}
