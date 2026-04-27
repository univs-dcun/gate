package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.domain.enums.ProjectType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

public record UpdateProjectRequestDTO(
        @Schema(description = SwaggerDescriptions.PROJECT_NAME)
        @Length(max = 255, message = "INVALID_PROJECT_NAME_LENGTH")
        String projectName,

        @Schema(description = SwaggerDescriptions.PROJECT_DESCRIPTION)
        @Length(max = 1000, message = "INVALID_PROJECT_DESCRIPTION_LENGTH")
        String projectDescription,

        @Schema(description = SwaggerDescriptions.PROJECT_TYPE)
        ProjectType projectType
) {
}
