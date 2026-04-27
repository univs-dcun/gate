package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.PasswordHistory;

import java.util.Optional;

public interface PasswordHistoryRepository {

    PasswordHistory save(PasswordHistory passwordHistory);

    Optional<PasswordHistory> findTop1ByAccountIdOrderByChangedAtDesc(Long accountId);
}
