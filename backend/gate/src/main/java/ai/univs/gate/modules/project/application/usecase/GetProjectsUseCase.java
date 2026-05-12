package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.application.result.ProjectsResult;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetProjectsUseCase {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public ProjectsResult execute(ProjectQuery query) {
        long totalCount = projectRepository.countByAccountIdAndIsDeletedFalse(query.accountId());
        var pagedProjects = projectRepository.findByAccountIdAndIsDeletedFalse(query);

        List<ProjectSummaryResult> results = pagedProjects.getContent();

        CustomPageResult page = CustomPageResult.from(
                pagedProjects.getPageable(),
                pagedProjects.getTotalElements(),
                pagedProjects.getTotalPages(),
                totalCount);

        return new ProjectsResult(results, page);
    }
}
