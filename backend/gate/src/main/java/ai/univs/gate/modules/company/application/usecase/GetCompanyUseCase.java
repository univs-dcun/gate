package ai.univs.gate.modules.company.application.usecase;

import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetCompanyUseCase {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CompanyResult execute(Long accountId) {
        Company company = companyRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CustomGateException(ErrorType.COMPANY_NOT_FOUND));

        return CompanyResult.from(company);
    }
}
