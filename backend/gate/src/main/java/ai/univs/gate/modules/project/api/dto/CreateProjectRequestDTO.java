package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.input.CreateProjectInput;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record CreateProjectRequestDTO(
        @Schema(description = SwaggerDescriptions.PROJECT_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_PROJECT_NAME")
        @Length(min = 1, max = 255, message = "INVALID_PROJECT_NAME_LENGTH")
        String projectName,

        @Schema(description = SwaggerDescriptions.PROJECT_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_PROJECT_DESCRIPTION_LENGTH")
        String projectDescription,

        @Schema(description = SwaggerDescriptions.PROJECT_TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "REQUIRED_PROJECT_TYPE")
        ProjectType projectType,

        @Schema(description = SwaggerDescriptions.PROJECT_MODULE_TYPE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "REQUIRED_PROJECT_MODULE_TYPE")
        ProjectModuleType projectModuleType
) {

        public CreateProjectInput toCreateProjectInput(Long accountId) {
                return new CreateProjectInput(
                        accountId,
                        projectName,
                        projectDescription,
                        projectType,
                        projectModuleType);
        }
}
