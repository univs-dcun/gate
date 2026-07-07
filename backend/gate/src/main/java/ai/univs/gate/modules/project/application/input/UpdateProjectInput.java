package ai.univs.gate.modules.project.application.input;


public record UpdateProjectInput(
        Long accountId,
        Long projectId,
        String projectName,
        String description,
        String colorTag
) {
}
