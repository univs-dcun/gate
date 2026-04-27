package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public Account save(Account account) {
        return accountJpaRepository.save(account);
    }

    @Override
    public Optional<Account> findById(Long id) {
        return accountJpaRepository.findById(id);
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return accountJpaRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return accountJpaRepository.existsByEmail(email);
    }
}
