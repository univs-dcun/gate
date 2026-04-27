package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectSettingsJpaRepository extends JpaRepository<ProjectSettings, Long> {

    ProjectSettings save(ProjectSettings settings);

    Optional<ProjectSettings> findByProject(Project projectId);
}
