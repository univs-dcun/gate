package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.Account;

import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(Long id);

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
