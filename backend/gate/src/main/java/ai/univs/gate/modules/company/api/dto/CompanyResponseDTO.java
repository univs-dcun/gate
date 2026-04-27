package ai.univs.gate.modules.company.api.dto;

import ai.univs.gate.modules.company.application.result.CompanyResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;

public record CompanyResponseDTO(
        @Schema(description = SwaggerDescriptions.COMPANY_ID)
        Long companyId,
        @Schema(description = SwaggerDescriptions.ACCOUNT_ID)
        Long accountId,
        @Schema(description = SwaggerDescriptions.COMPANY_NAME)
        String companyName,
        @Schema(description = SwaggerDescriptions.BUSINESS_NUMBER)
        String businessNumber,
        @Schema(description = SwaggerDescriptions.MANAGER_MAIL)
        String managerMail,
        @Schema(description = SwaggerDescriptions.MANAGER_NAME)
        String managerName,
        @Schema(description = SwaggerDescriptions.MANAGER_NUMBER)
        String managerNumber,
        @Schema(description = SwaggerDescriptions.MAIN_SERVICE)
        String mainService,
        @Schema(description = SwaggerDescriptions.BUSINESS_TYPE)
        String businessType,
        @Schema(description = SwaggerDescriptions.EMPLOYEE_COUNT)
        String employeeCount
) {

    public static CompanyResponseDTO from(CompanyResult result) {
        return new CompanyResponseDTO(
                result.companyId(),
                result.accountId(),
                result.companyName(),
                result.businessNumber(),
                result.managerMail(),
                result.managerName(),
                result.managerNumber(),
                result.mainService(),
                result.businessType(),
                result.employeeCount());
    }
}
