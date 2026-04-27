package ai.univs.gate.modules.match.infrastructure.persistence;

import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.entity.QMatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.shared.utils.CustomPageable;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MatchHistoryDSLRepository {

    private final JPAQueryFactory queryFactory;

    public MatchHistoryDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    private final QMatchHistory matchHistory = QMatchHistory.matchHistory;

    public Page<MatchHistory> findAllByQuery(MatchHistoryQuery query, Long projectId) {
        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        var orderSpecifiers = createOrderSpecifiers();
        var booleanBuilder = createBooleanBuilder(query, projectId);

        List<MatchHistory> fetch = queryFactory
                .selectFrom(matchHistory)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(matchHistory.count())
                .from(matchHistory)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(fetch, pageable, total);
    }

    private List<OrderSpecifier<?>> createOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, matchHistory.matchTime));
        return orderSpecifiers;
    }

    private BooleanBuilder createBooleanBuilder(MatchHistoryQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        booleanBuilder.and(matchHistory.project.id.eq(projectId));

        if (StringUtils.hasText(query.matchingHistoryKeyword())) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(matchHistory.transactionUuid.containsIgnoreCase(query.matchingHistoryKeyword()));
            keywordBuilder.or(matchHistory.faceId.containsIgnoreCase(query.matchingHistoryKeyword()));
            keywordBuilder.or(matchHistory.userDescription.containsIgnoreCase(query.matchingHistoryKeyword()));
            booleanBuilder.and(keywordBuilder);
        }

        // 매치 타입이 지정된 경우
        if (!query.matchType().equals("ALL")) {
            MatchType matchType = MatchType.valueOf(query.matchType());
            booleanBuilder.and(matchHistory.matchType.eq(matchType));
        }

        if ("SUCCESS".equals(query.matchResultType())) {
            booleanBuilder.and(matchHistory.success.eq(true));
        }
        else if ("FAILURE".equals(query.matchResultType())) {
            booleanBuilder.and(matchHistory.success.eq(false));
        }

        if (query.hasDate()) {
            booleanBuilder.and(matchHistory.matchTime.goe(query.startDateTime()));
            booleanBuilder.and(matchHistory.matchTime.loe(query.endDateTime()));
        }

        return booleanBuilder;
    }
}
