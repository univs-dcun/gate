package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.enums.ProjectType;

import java.time.LocalDateTime;

public record ProjectResult(
        Long projectId,
        String projectName,
        String projectDescription,
        ProjectStatus status,
        ProjectType projectType,
        ProjectModuleType projectModuleType,
        String packageKey,
        String apiKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProjectResult from(Project project, String apiKey) {
        return new ProjectResult(
                project.getId(),
                project.getProjectName(),
                project.getProjectDescription(),
                project.getStatus(),
                project.getProjectType(),
                project.getProjectModuleType(),
                project.getPackageKey(),
                apiKey,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public static ProjectResult from(Project project) {
        return from(project, "");
    }
}
