package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectUseCase {

    private final ProjectService projectService;
    private final ApiKeyService apiKeyService;

    @Transactional(readOnly = true)
    public ProjectResult execute(Long accountId, Long projectId) {
        Project project = projectService.validateOwnership(projectId, accountId);
        ApiKey apiKey = apiKeyService.findByProject(project);
        return ProjectResult.from(project, apiKey.getApiKey());
    }
}
