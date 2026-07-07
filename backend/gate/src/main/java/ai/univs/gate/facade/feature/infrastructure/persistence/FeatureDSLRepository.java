package ai.univs.gate.facade.feature.infrastructure.persistence;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.modules.feature.domain.entity.QBiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeatureDSLRepository {

    private final JPAQueryFactory queryFactory;
    private final QBiometricFeature bf = QBiometricFeature.biometricFeature;

    public FeatureDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public List<FeatureRow> findFaceRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return queryFactory
                .select(Projections.constructor(FeatureRow.class,
                        Expressions.constant("FACE"),
                        bf.id,
                        bf.description,
                        bf.featureImagePath,
                        bf.featureId,
                        bf.createdAt))
                .from(bf)
                .where(buildWhere(projectId, query, FeatureType.FACE))
                .orderBy(bf.createdAt.desc(), bf.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long countFace(Long projectId, FeatureListQuery query) {
        Long result = queryFactory
                .select(bf.count())
                .from(bf)
                .where(buildWhere(projectId, query, FeatureType.FACE))
                .fetchOne();
        return result != null ? result : 0L;
    }

    public List<FeatureRow> findPalmRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return queryFactory
                .select(Projections.constructor(FeatureRow.class,
                        Expressions.constant("PALM"),
                        bf.id,
                        bf.description,
                        bf.featureImagePath,
                        bf.featureId,
                        bf.createdAt))
                .from(bf)
                .where(buildWhere(projectId, query, FeatureType.PALM))
                .orderBy(bf.createdAt.desc(), bf.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long countPalm(Long projectId, FeatureListQuery query) {
        Long result = queryFactory
                .select(bf.count())
                .from(bf)
                .where(buildWhere(projectId, query, FeatureType.PALM))
                .fetchOne();
        return result != null ? result : 0L;
    }

    private BooleanBuilder buildWhere(Long projectId, FeatureListQuery query, FeatureType featureType) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(bf.project.id.eq(projectId));
        b.and(bf.type.eq(featureType));
        if (query.isDeleted() != null) {
            b.and(bf.isDeleted.eq(query.isDeleted()));
        } else {
            b.and(bf.isDeleted.eq(false));
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            BooleanBuilder kw = new BooleanBuilder();
            kw.or(bf.featureId.containsIgnoreCase(query.keyword()));
            kw.or(bf.description.containsIgnoreCase(query.keyword()));
            b.and(kw);
        }
        if (query.hasDate()) {
            b.and(bf.createdAt.goe(query.startDateTime()));
            b.and(bf.createdAt.loe(query.endDateTime()));
        }
        return b;
    }
}
