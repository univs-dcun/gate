package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.domain.repository.ActivityLogRepository;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ActivityLogRepositoryImpl implements ActivityLogRepository {

    private final ActivityLogDSLRepository dsl;

    @Override
    public Page<ActivityLog> findAllByQuery(MatchHistoryQuery query, Long projectId) {
        return dsl.findAllByQuery(query, projectId);
    }

    @Override
    public Optional<ActivityLog> findLatestByProjectIdAndTransactionUuid(Long projectId, String transactionUuid) {
        return dsl.findLatestByProjectIdAndTransactionUuid(projectId, transactionUuid);
    }

    @Override
    public long countByProjectId(Long projectId, boolean includeDeletions) {
        return dsl.countByProjectId(projectId, includeDeletions);
    }
}
