package ai.univs.gate.modules.company.application.usecase;

import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetCompanyUseCase {

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyResult execute(Long accountId, String email) {
        Company company = companyRepository.findByAccountId(accountId)
                .orElseGet(() -> createCompany(accountId, email));

        return CompanyResult.from(company);
    }

    // 첫 조회 시 lazy 생성 — account_id unique 제약이 동시 요청의 중복 생성을 막는다
    private Company createCompany(Long accountId, String email) {
        Company company = companyRepository.save(Company.builder()
                .accountId(accountId)
                .managerMail(email)
                .build());
        log.info("Company lazily created on first access: accountId={}, companyId={}", accountId, company.getId());
        return company;
    }
}
