package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.domain.entity.ConsentLog;
import ai.univs.gate.modules.project.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentLogJpaRepository extends JpaRepository<ConsentLog, Long> {

    List<ConsentLog> findByProjectOrderByCreatedAtDesc(Project project);
}
