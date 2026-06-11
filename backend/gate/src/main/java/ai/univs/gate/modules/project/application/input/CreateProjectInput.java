package ai.univs.gate.modules.project.application.input;


public record CreateProjectInput(
        Long accountId,
        String projectName,
        String projectDescription,
        String colorTag
) {
}
