package ai.univs.gate.modules.palm_media.infrastructure.persistence;

import ai.univs.gate.modules.palm_media.application.input.PalmMediaQuery;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.entity.QPalmMedia;
import ai.univs.gate.shared.auth.UserContext;
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

import java.util.ArrayList;
import java.util.List;

@Repository
public class PalmMediaDSLRepository {

    private final JPAQueryFactory queryFactory;
    private final QPalmMedia palmMedia = QPalmMedia.palmMedia;

    public PalmMediaDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Page<PalmMedia> findAllByQuery(PalmMediaQuery query, Long projectId) {
        List<OrderSpecifier<?>> orderSpecifiers = buildOrderSpecifiers();
        BooleanBuilder booleanBuilder = buildConditionBuilder(query, projectId);

        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        List<PalmMedia> fetch = queryFactory
                .selectFrom(palmMedia)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(palmMedia.count())
                .from(palmMedia)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(fetch, pageable, total);
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.ASC, palmMedia.id));
        return orderSpecifiers;
    }

    private BooleanBuilder buildConditionBuilder(PalmMediaQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        booleanBuilder.and(palmMedia.project.id.eq(projectId));

        if (query.keyword() != null && !query.keyword().isEmpty()) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(palmMedia.palmId.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(palmMedia.description.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(palmMedia.transactionUuid.containsIgnoreCase(query.keyword()));
            booleanBuilder.and(keywordBuilder);
        }

        booleanBuilder.and(palmMedia.isDeleted.eq(false));

        UserContext userContext = UserContext.get();
        if (query.hasDate()) {
            booleanBuilder.and(palmMedia.createdAt.goe(query.getStartDateTime(userContext.getTimezone())));
            booleanBuilder.and(palmMedia.createdAt.loe(query.getEndDateTime(userContext.getTimezone())));
        }

        return booleanBuilder;
    }
}
