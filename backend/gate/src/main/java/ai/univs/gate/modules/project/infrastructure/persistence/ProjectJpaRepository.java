package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectJpaRepository extends JpaRepository<Project, Long> {

    Project save(Project project);

    Optional<Project> findByIdAndIsDeletedFalse(Long id);

    long countByAccountIdAndIsDeletedFalse(Long userId);
}
