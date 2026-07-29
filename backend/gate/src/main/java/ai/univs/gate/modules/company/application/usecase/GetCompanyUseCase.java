package ai.univs.gate.modules.company.application.usecase;

import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.modules.company.domain.entity.Company;
import ai.univs.gate.modules.company.domain.repository.CompanyRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetCompanyUseCase {

    private final CompanyRepository companyRepository;

    // 의도적으로 트랜잭션을 걸지 않는다 — 동시 첫 조회 경합 시 save 실패를
    // 재조회로 복구하려면 바깥 트랜잭션이 rollback-only로 오염되면 안 된다
    public CompanyResult execute(Long accountId, String email) {
        if (accountId == null) {
            throw new CustomGateException(ErrorType.INVALID_INPUT);
        }

        Company company = companyRepository.findByAccountId(accountId)
                .orElseGet(() -> createCompany(accountId, email));

        return CompanyResult.from(company);
    }

    // 첫 조회 시 lazy 생성 — 기존 가입 시 초기화(internal/init)와 동일한 빈 문자열 형태 유지
    private Company createCompany(Long accountId, String email) {
        Company company = Company.builder()
                .accountId(accountId)
                .companyName("")
                .businessNumber("")
                .managerMail(email != null ? email : "")
                .managerName("")
                .managerNumber("")
                .mainService("")
                .businessType("")
                .employeeCount("")
                .build();
        try {
            Company saved = companyRepository.save(company);
            log.info("Company lazily created on first access: accountId={}, companyId={}", accountId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // uq_companies_account_id 위반 = 동시 요청이 먼저 생성함 — 그 행을 재조회
            return companyRepository.findByAccountId(accountId).orElseThrow(() -> e);
        }
    }
}
