package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record ProjectsResponseDTO(
        List<ProjectResponseDTO> contents,
        CustomPage page
) {
}
