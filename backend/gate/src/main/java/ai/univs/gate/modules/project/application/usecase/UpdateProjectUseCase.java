package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.UpdateProjectInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateProjectUseCase {

    private final ProjectService projectService;

    @Transactional
    public ProjectResult execute(UpdateProjectInput input) {
        Project project = projectService.validateOwnership(input.projectId(), input.accountId());

        project.updateInfo(input.projectName(), input.description());

        if (input.projectType() != null) {
            project.updateProjectType(input.projectType());
        }

        return ProjectResult.from(project);
    }
}
