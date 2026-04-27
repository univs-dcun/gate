package ai.univs.gate.modules.project.application.input;

import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectType;

public record CreateProjectInput(
        Long accountId,
        String projectName,
        String projectDescription,
        ProjectType projectType,
        ProjectModuleType projectModuleType
) {
}
