package ai.univs.gate.modules.feature.domain.repository;

import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
import java.util.Optional;
import org.springframework.data.domain.Page;

/** 통합 이력 조회 (UG-326). 쓰기는 없다 — 원본은 match_history·feature_history 다. */
public interface ActivityLogRepository {
    Page<ActivityLog> findAllByQuery(MatchHistoryQuery query, Long projectId);
    Optional<ActivityLog> findLatestByProjectIdAndTransactionUuid(Long projectId, String transactionUuid);
    /** 프로젝트 전체 이력 수 (검색 조건 무관). {@code includeDeletions} 만 따른다 — 목록과 같은 모집단이어야 한다. */
    long countByProjectId(Long projectId, boolean includeDeletions);
}
