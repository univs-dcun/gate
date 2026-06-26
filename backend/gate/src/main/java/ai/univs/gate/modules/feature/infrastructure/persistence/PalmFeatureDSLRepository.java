package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.palm_feature.application.input.PalmFeatureQuery;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import ai.univs.gate.modules.palm_feature.domain.entity.QPalmFeature;
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
public class PalmFeatureDSLRepository {

    private final JPAQueryFactory queryFactory;
    private final QPalmFeature palmFeature = QPalmFeature.palmFeature;

    public PalmFeatureDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Page<PalmFeature> findAllByQuery(PalmFeatureQuery query, Long projectId) {
        List<OrderSpecifier<?>> orderSpecifiers = buildOrderSpecifiers();
        BooleanBuilder booleanBuilder = buildConditionBuilder(query, projectId);

        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        List<PalmFeature> fetch = queryFactory
                .selectFrom(palmFeature)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(palmFeature.count())
                .from(palmFeature)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(fetch, pageable, total);
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.ASC, palmFeature.id));
        return orderSpecifiers;
    }

    private BooleanBuilder buildConditionBuilder(PalmFeatureQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        booleanBuilder.and(palmFeature.project.id.eq(projectId));

        if (query.keyword() != null && !query.keyword().isEmpty()) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(palmFeature.featureId.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(palmFeature.description.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(palmFeature.transactionUuid.containsIgnoreCase(query.keyword()));
            booleanBuilder.and(keywordBuilder);
        }

        booleanBuilder.and(palmFeature.isDeleted.eq(false));

        UserContext userContext = UserContext.get();
        if (query.hasDate()) {
            booleanBuilder.and(palmFeature.createdAt.goe(query.getStartDateTime(userContext.getTimezone())));
            booleanBuilder.and(palmFeature.createdAt.loe(query.getEndDateTime(userContext.getTimezone())));
        }

        return booleanBuilder;
    }
}
