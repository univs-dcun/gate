package ai.univs.gate.modules.company.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

public record UpsertCompanyRequestDTO(
        @Schema(description = SwaggerDescriptions.COMPANY_NAME)
        @Length(max = 100, message = "INVALID_COMPANY_NAME_LENGTH")
        String companyName,

        @Schema(description = SwaggerDescriptions.BUSINESS_NUMBER)
        @Length(max = 20, message = "INVALID_BUSINESS_NUMBER_LENGTH")
        String businessNumber,

        @Schema(description = SwaggerDescriptions.MANAGER_MAIL)
        @Length(max = 100, message = "INVALID_MANAGER_MAIL_LENGTH")
        String managerMail,

        @Schema(description = SwaggerDescriptions.MANAGER_NAME)
        @Length(max = 100, message = "INVALID_MANAGER_NAME_LENGTH")
        String managerName,

        @Schema(description = SwaggerDescriptions.MANAGER_NUMBER)
        @Length(max = 100, message = "INVALID_MANAGER_NUMBER_LENGTH")
        String managerNumber,

        @Schema(description = SwaggerDescriptions.MAIN_SERVICE)
        @Length(max = 100, message = "INVALID_MAIN_SERVICE_LENGTH")
        String mainService,

        @Schema(description = SwaggerDescriptions.BUSINESS_TYPE)
        @Length(max = 100, message = "INVALID_BUSINESS_TYPE_LENGTH")
        String businessType,

        @Schema(description = SwaggerDescriptions.EMPLOYEE_COUNT)
        @Length(max = 100, message = "INVALID_EMPLOYEE_COUNT_LENGTH")
        String employeeCount
) {
}
