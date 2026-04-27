package ai.univs.gate.modules.match.infrastructure.persistence;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.project.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchHistoryJpaRepository extends JpaRepository<MatchHistory, Long> {

    Optional<MatchHistory> findTopByProjectAndTransactionUuidOrderByCreatedAtDesc(Project project,
                                                                                  String transactionUuid);

    long countByProject(Project project);
}
