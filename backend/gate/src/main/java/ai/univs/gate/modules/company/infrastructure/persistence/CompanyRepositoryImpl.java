package ai.univs.gate.modules.company.infrastructure.persistence;

import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepository {

    private final CompanyJpaRepository companyJpaRepository;

    @Override
    public Company save(Company company) {
        return companyJpaRepository.save(company);
    }

    @Override
    public Optional<Company> findByAccountId(Long accountId) {
        return companyJpaRepository.findByAccountId(accountId);
    }

    @Override
    public boolean existsByAccountId(Long accountId) {
        return companyJpaRepository.existsByAccountId(accountId);
    }
}
