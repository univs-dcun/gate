package ai.univs.gate.modules.project.application.result;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;

import java.time.LocalDateTime;

public record ProjectResult(
        Long projectId,
        String projectName,
        String projectDescription,
        ProjectStatus status,
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
                apiKey,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public static ProjectResult from(Project project) {
        return from(project, "");
    }
}
