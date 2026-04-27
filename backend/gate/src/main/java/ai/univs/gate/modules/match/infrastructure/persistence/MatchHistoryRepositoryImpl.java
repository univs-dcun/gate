package ai.univs.gate.modules.match.infrastructure.persistence;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.match.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.modules.project.domain.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchHistoryRepositoryImpl implements MatchHistoryRepository {

    private final MatchHistoryJpaRepository matchHistoryJpaRepository;
    private final MatchHistoryDSLRepository matchHistoryDSLRepository;

    @Override
    public MatchHistory save(MatchHistory matchHistory) {
        return matchHistoryJpaRepository.save(matchHistory);
    }

    @Override
    public Optional<MatchHistory> findTopByProjectAndTransactionUuidOrderByCreatedAtDesc(Project project,
                                                                                         String transactionUuid
    ) {
        return matchHistoryJpaRepository.findTopByProjectAndTransactionUuidOrderByCreatedAtDesc(
                project, transactionUuid);
    }

    @Override
    public Page<MatchHistory> findAllByQuery(MatchHistoryQuery query, Project project) {
        return matchHistoryDSLRepository.findAllByQuery(query, project.getId());
    }

    @Override
    public long countByProject(Project project) {
        return matchHistoryJpaRepository.countByProject(project);
    }
}
