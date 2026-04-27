package ai.univs.gate.modules.match.domain.repository;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.modules.project.domain.entity.Project;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface MatchHistoryRepository {

    MatchHistory save(MatchHistory matchHistory);

    Optional<MatchHistory> findTopByProjectAndTransactionUuidOrderByCreatedAtDesc(Project project,
                                                                                  String transactionUuid);

    Page<MatchHistory> findAllByQuery(MatchHistoryQuery query, Project project);

    long countByProject(Project project);
}
