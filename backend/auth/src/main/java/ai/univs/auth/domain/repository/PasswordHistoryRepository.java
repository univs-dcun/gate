package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.PasswordHistory;

import java.util.List;

public interface PasswordHistoryRepository {

    PasswordHistory save(PasswordHistory passwordHistory);

    List<PasswordHistory> findRecentByAccountId(Long accountId, int limit);
}
