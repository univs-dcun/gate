package ai.univs.gate.support.company;

import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    public void createInitial(Long accountId, String managerMail) {
        Company company = Company.builder()
                .accountId(accountId)
                .managerMail(managerMail)
                .build();
        companyRepository.save(company);
    }
}
