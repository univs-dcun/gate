package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectService projectService;

    @Transactional
    public void execute(Long accountId, Long projectId) {
        Project project = projectService.validateOwnership(projectId, accountId);
        project.delete();
    }
}
