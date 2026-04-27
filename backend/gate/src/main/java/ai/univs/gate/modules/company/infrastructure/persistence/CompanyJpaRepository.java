package ai.univs.gate.modules.company.infrastructure.persistence;

import ai.univs.gate.modules.company.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyJpaRepository extends JpaRepository<Company, Long> {

    Company save(Company company);

    Optional<Company> findByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);
}
