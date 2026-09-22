package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.ActivityLog;
import ai.univs.gate.modules.feature.domain.entity.QActivityLog;
import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.shared.utils.CustomPageable;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 통합 이력 목록 (UG-326). {@code MatchHistoryDSLRepository} 를 대체한다 — 필터 의미는 그대로 두고
 * 대상만 {@link ActivityLog}(match_history ∪ feature_history) 로 바꿨다.
 */
@Repository
public class ActivityLogDSLRepository {

    private final JPAQueryFactory queryFactory;
    private final QActivityLog log = QActivityLog.activityLog;

    public ActivityLogDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Page<ActivityLog> findAllByQuery(MatchHistoryQuery query, Long projectId) {
        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());
        BooleanBuilder where = where(query, projectId);

        // 정렬 키는 사건 시퀀스(activity_seq) — 두 테이블이 공유하므로 시간순과 단조 일치하고(UG-328),
        // 동시각에도 유일해 페이지 경계에서 행이 흔들리지 않는다.
        List<ActivityLog> fetch = queryFactory
                .selectFrom(log)
                .where(where)
                .orderBy(log.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory.select(log.count()).from(log).where(where).fetchOne();
        return new PageImpl<>(fetch, pageable, total == null ? 0 : total);
    }

    public Optional<ActivityLog> findLatestByProjectIdAndTransactionUuid(Long projectId, String transactionUuid) {
        return Optional.ofNullable(queryFactory
                .selectFrom(log)
                .where(log.projectId.eq(projectId), log.transactionUuid.eq(transactionUuid))
                .orderBy(log.id.desc())
                .fetchFirst());
    }

    public long countByProjectId(Long projectId, boolean includeDeletions) {
        BooleanBuilder where = new BooleanBuilder().and(log.projectId.eq(projectId));
        if (!includeDeletions) {
            where.and(log.activityType.ne(ActivityType.DELETE));
        }
        Long total = queryFactory.select(log.count()).from(log).where(where).fetchOne();
        return total == null ? 0 : total;
    }

    private BooleanBuilder where(MatchHistoryQuery query, Long projectId) {
        BooleanBuilder where = new BooleanBuilder().and(log.projectId.eq(projectId));

        if (StringUtils.hasText(query.matchingHistoryKeyword())) {
            String kw = query.matchingHistoryKeyword();
            where.and(new BooleanBuilder()
                    .or(log.transactionUuid.containsIgnoreCase(kw))
                    .or(log.featureId.containsIgnoreCase(kw))
                    .or(log.userDescription.containsIgnoreCase(kw))
                    // UG-333: 고객사 식별자로도 찾는다 — 재등록 전후를 한 사람으로 이어 보는 진입점
                    .or(log.externalKey.containsIgnoreCase(kw)));
        }

        // 기능 필터. "ALL" 은 옛 클라이언트 호환을 위해 기본으로 DELETE 를 숨긴다 — 옛 프론트의
        // MatchingType 에 DELETE 가 없어 라벨이 비고 아이콘이 등록으로 폴백된다. DELETE 를 명시해 고르면
        // 그 자체가 요청이므로 includeDeletions 와 무관하게 내려준다.
        if ("ALL".equals(query.matchType())) {
            if (!query.includeDeletions()) {
                where.and(log.activityType.ne(ActivityType.DELETE));
            }
        } else {
            where.and(log.activityType.eq(ActivityType.valueOf(query.matchType())));
        }

        if (!"ALL".equals(query.featureType())) {
            where.and(log.featureType.eq(FeatureType.valueOf(query.featureType())));
        }
        if ("SUCCESS".equals(query.matchResultType())) {
            where.and(log.success.isTrue());
        } else if ("FAILURE".equals(query.matchResultType())) {
            where.and(log.success.isFalse());
        }
        if (query.hasDate()) {
            where.and(log.createdAt.goe(query.startDateTime()));
            where.and(log.createdAt.loe(query.endDateTime()));
        }
        return where;
    }
}
