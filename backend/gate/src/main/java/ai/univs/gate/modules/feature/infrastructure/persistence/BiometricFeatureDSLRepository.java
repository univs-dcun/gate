package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.QBiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
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
public class BiometricFeatureDSLRepository {

    private final JPAQueryFactory queryFactory;
    private final QBiometricFeature biometricFeature = QBiometricFeature.biometricFeature;

    public BiometricFeatureDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Page<BiometricFeature> findAllByQuery(BiometricFeatureQuery query, Long projectId) {
        List<OrderSpecifier<?>> orderSpecifiers = buildOrderSpecifiers();
        BooleanBuilder booleanBuilder = buildConditionBuilder(query, projectId);

        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        List<BiometricFeature> fetch = queryFactory
                .selectFrom(biometricFeature)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(biometricFeature.count())
                .from(biometricFeature)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(fetch, pageable, total != null ? total : 0L);
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.ASC, biometricFeature.id));
        return orderSpecifiers;
    }

    private BooleanBuilder buildConditionBuilder(BiometricFeatureQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        booleanBuilder.and(biometricFeature.project.id.eq(projectId));
        booleanBuilder.and(biometricFeature.type.eq(query.type()));
        booleanBuilder.and(biometricFeature.isDeleted.eq(false));

        if (query.keyword() != null && !query.keyword().isEmpty()) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(biometricFeature.featureId.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(biometricFeature.description.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(biometricFeature.transactionUuid.containsIgnoreCase(query.keyword()));
            booleanBuilder.and(keywordBuilder);
        }

        UserContext userContext = UserContext.get();
        if (query.hasDate()) {
            booleanBuilder.and(biometricFeature.createdAt.goe(query.getStartDateTime(userContext.getTimezone())));
            booleanBuilder.and(biometricFeature.createdAt.loe(query.getEndDateTime(userContext.getTimezone())));
        }

        return booleanBuilder;
    }
}
