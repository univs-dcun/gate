package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordHistoryRepositoryImpl implements PasswordHistoryRepository {

    private final PasswordHistoryJpaRepository passwordHistoryJpaRepository;

    @Override
    public PasswordHistory save(PasswordHistory passwordHistory) {
        return passwordHistoryJpaRepository.save(passwordHistory);
    }

    @Override
    public Optional<PasswordHistory> findTop1ByAccountIdOrderByChangedAtDesc(Long accountId) {
        return passwordHistoryJpaRepository.findTop1ByAccountIdOrderByChangedAtDesc(accountId);
    }
}
