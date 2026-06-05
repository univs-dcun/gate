package ai.univs.gate.modules.face_feature.infrastructure.persistence;

import ai.univs.gate.modules.face_feature.application.input.FaceFeatureQuery;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.entity.QFaceFeature;
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
public class FaceFeatureDSLRepository {

    private final JPAQueryFactory queryFactory;

    public FaceFeatureDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    private final QFaceFeature faceFeature = QFaceFeature.faceFeature;

    public Page<FaceFeature> findAllByQuery(FaceFeatureQuery query, Long projectId) {
        List<OrderSpecifier<?>> orderSpecifiers = buildOrderSpecifiers();
        BooleanBuilder booleanBuilder = buildConditionBuilder(query, projectId);

        Pageable pageable = CustomPageable.of(query.page(), query.pageSize());

        List<FaceFeature> fetch = queryFactory
                .selectFrom(faceFeature)
                .where(booleanBuilder)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(faceFeature.count())
                .from(faceFeature)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(fetch, pageable, total);
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers() {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(new OrderSpecifier<>(Order.ASC, faceFeature.id));
        return orderSpecifiers;
    }

    private BooleanBuilder buildConditionBuilder(FaceFeatureQuery query, Long projectId) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        booleanBuilder.and(faceFeature.project.id.eq(projectId));

        if (query.keyword() != null && !query.keyword().isEmpty()) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();
            keywordBuilder.or(faceFeature.featureId.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(faceFeature.description.containsIgnoreCase(query.keyword()));
            keywordBuilder.or(faceFeature.transactionUuid.containsIgnoreCase(query.keyword()));
            booleanBuilder.and(keywordBuilder);
        }

        booleanBuilder.and(faceFeature.isDeleted.eq(false));

        UserContext userContext = UserContext.get();
        if (query.hasDate()) {
            booleanBuilder.and(faceFeature.createdAt.goe(query.getStartDateTime(userContext.getTimezone())));
            booleanBuilder.and(faceFeature.createdAt.loe(query.getEndDateTime(userContext.getTimezone())));
        }

        return booleanBuilder;
    }
}
