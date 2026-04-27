package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectJpaRepository projectJpaRepository;
    private final ProjectDSLRepository projectDSLRepository;

    @Override
    public Project save(Project project) {
        return projectJpaRepository.save(project);
    }

    @Override
    public Page<ProjectSummaryResult> findByAccountIdAndIsDeletedFalse(ProjectQuery query) {
        return projectDSLRepository.findByAccountIdAndIsDeletedFalse(query);
    }

    @Override
    public Optional<Project> findByIdAndIsDeletedFalse(Long id) {
        return projectJpaRepository.findByIdAndIsDeletedFalse(id);
    }

    @Override
    public long countByAccountIdAndIsDeletedFalse(Long userId) {
        return projectJpaRepository.countByAccountIdAndIsDeletedFalse(userId);
    }
}
