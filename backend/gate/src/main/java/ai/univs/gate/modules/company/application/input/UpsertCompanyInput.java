package ai.univs.gate.modules.company.application.input;

public record UpsertCompanyInput(
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

}
