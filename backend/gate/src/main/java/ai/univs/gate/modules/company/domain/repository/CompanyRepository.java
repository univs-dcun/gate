package ai.univs.gate.modules.company.domain.repository;

import ai.univs.gate.modules.company.domain.entity.Company;

import java.util.Optional;

public interface CompanyRepository {

    Company save(Company companyInfo);

    Optional<Company> findByAccountId(Long userId);

    boolean existsByAccountId(Long userId);
}
