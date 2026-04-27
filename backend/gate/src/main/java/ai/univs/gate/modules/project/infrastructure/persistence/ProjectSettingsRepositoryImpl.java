package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectSettingsRepositoryImpl implements ProjectSettingsRepository {

    private final ProjectSettingsJpaRepository projectSettingsJpaRepository;

    @Override
    public ProjectSettings save(ProjectSettings projectSettings) {
        return projectSettingsJpaRepository.save(projectSettings);
    }

    @Override
    public Optional<ProjectSettings> findByProject(Project project) {
        return projectSettingsJpaRepository.findByProject(project);
    }
}
