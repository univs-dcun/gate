package ai.univs.gate.modules.company.application.result;

import ai.univs.gate.modules.company.domain.entity.Company;

public record CompanyResult(
        Long companyId,
        Long accountId,
        String companyName,
        String businessNumber,
        String managerMail,
        String managerName,
        String managerNumber,
        String mainService,
        String businessType,
        String employeeCount
) {

    public static CompanyResult from(Company company) {
        return new CompanyResult(
                company.getId(),
                company.getAccountId(),
                company.getCompanyName(),
                company.getBusinessNumber(),
                company.getManagerMail(),
                company.getManagerName(),
                company.getManagerNumber(),
                company.getMainService(),
                company.getBusinessType(),
                company.getEmployeeCount());
    }
}
