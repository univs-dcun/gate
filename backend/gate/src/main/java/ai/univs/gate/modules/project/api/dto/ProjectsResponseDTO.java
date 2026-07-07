package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.dto.CustomPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ProjectsResponseDTO(
        @Schema(description = SwaggerDescriptions.PROJECT_LIST)
        List<ProjectResponseDTO> contents,

        @Schema(description = SwaggerDescriptions.PAGE_INFO)
        CustomPage page
) {
}
