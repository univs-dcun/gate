package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.domain.entity.ConsentLog;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.repository.ConsentLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ConsentLogRepositoryImpl implements ConsentLogRepository {

    private final ConsentLogJpaRepository consentLogJpaRepository;

    @Override
    public ConsentLog save(ConsentLog consentLog) {
        return consentLogJpaRepository.save(consentLog);
    }

    @Override
    public List<ConsentLog> findByProjectOrderByCreatedAtDesc(Project project) {
        return consentLogJpaRepository.findByProjectOrderByCreatedAtDesc(project);
    }
}
