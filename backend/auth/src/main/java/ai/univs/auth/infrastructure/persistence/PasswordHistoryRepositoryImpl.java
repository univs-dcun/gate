package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PasswordHistoryRepositoryImpl implements PasswordHistoryRepository {

    private final PasswordHistoryJpaRepository passwordHistoryJpaRepository;

    @Override
    public PasswordHistory save(PasswordHistory passwordHistory) {
        return passwordHistoryJpaRepository.save(passwordHistory);
    }

    @Override
    public List<PasswordHistory> findRecentByAccountId(Long accountId, int limit) {
        return passwordHistoryJpaRepository.findByAccountIdOrderByChangedAtDesc(accountId, PageRequest.of(0, limit));
    }
}
