package ai.univs.gate.modules.project.application.input;

import ai.univs.gate.modules.project.domain.enums.ProjectType;

public record UpdateProjectInput(
        Long accountId,
        Long projectId,
        String projectName,
        String description,
        ProjectType projectType
) {
}
