package ai.univs.gate.modules.company.application.usecase;

import ai.univs.gate.modules.company.application.input.UpsertCompanyInput;
import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpsertCompanyUseCase {

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyResult execute(UpsertCompanyInput input) {
        Optional<Company> existing = companyRepository.findByAccountId(input.accountId());

        Company company;
        if (existing.isEmpty()) {
            company = Company.builder()
                    .accountId(input.accountId())
                    .companyName(input.companyName())
                    .businessNumber(input.businessNumber())
                    .managerMail(input.managerMail())
                    .managerName(input.managerName())
                    .managerNumber(input.managerNumber())
                    .mainService(input.mainService())
                    .businessType(input.businessType())
                    .employeeCount(input.employeeCount())
                    .build();
            companyRepository.save(company);
            log.info("Company info created: accountId={}, companyId={}", company.getAccountId(), company.getId());
        } else {
            company = existing.get();
            company.updateInformation(
                    input.companyName(),
                    input.businessNumber(),
                    input.managerMail(),
                    input.managerName(),
                    input.managerNumber(),
                    input.mainService(),
                    input.businessType(),
                    input.employeeCount());
            log.info("Company info updated: accountId={}, companyId={}", company.getAccountId(), company.getId());
        }

        return CompanyResult.from(company);
    }
}
