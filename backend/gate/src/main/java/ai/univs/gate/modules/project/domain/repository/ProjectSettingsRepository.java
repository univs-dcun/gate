package ai.univs.gate.modules.project.domain.repository;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;

import java.util.Optional;

public interface ProjectSettingsRepository {

    ProjectSettings save(ProjectSettings settings);

    Optional<ProjectSettings> findByProject(Project project);
}
