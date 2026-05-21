package ai.univs.gate.modules.project.domain.repository;

import ai.univs.gate.modules.project.domain.entity.ConsentLog;
import ai.univs.gate.modules.project.domain.entity.Project;

import java.util.List;

public interface ConsentLogRepository {

    ConsentLog save(ConsentLog consentLog);

    List<ConsentLog> findByProjectOrderByCreatedAtDesc(Project project);
}
