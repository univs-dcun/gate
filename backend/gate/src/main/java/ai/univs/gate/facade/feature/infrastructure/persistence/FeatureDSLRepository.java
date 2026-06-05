package ai.univs.gate.facade.feature.infrastructure.persistence;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.modules.face_feature.domain.entity.QFaceFeature;
import ai.univs.gate.modules.palm_feature.domain.entity.QPalmFeature;
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
    private final QFaceFeature qf = QFaceFeature.faceFeature;
    private final QPalmFeature qp = QPalmFeature.palmFeature;

    public FeatureDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public List<FeatureRow> findFaceRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return queryFactory
                .select(Projections.constructor(FeatureRow.class,
                        Expressions.constant("FACE"),
                        qf.id,
                        qf.description,
                        qf.featureImagePath,
                        qf.featureId,
                        qf.createdAt))
                .from(qf)
                .where(buildFaceWhere(projectId, query))
                .orderBy(qf.createdAt.desc(), qf.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long countFace(Long projectId, FeatureListQuery query) {
        Long result = queryFactory
                .select(qf.count())
                .from(qf)
                .where(buildFaceWhere(projectId, query))
                .fetchOne();
        return result != null ? result : 0L;
    }

    public List<FeatureRow> findPalmRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return queryFactory
                .select(Projections.constructor(FeatureRow.class,
                        Expressions.constant("PALM"),
                        qp.id,
                        qp.description,
                        qp.featureImagePath,
                        qp.featureId,
                        qp.createdAt))
                .from(qp)
                .where(buildPalmWhere(projectId, query))
                .orderBy(qp.createdAt.desc(), qp.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long countPalm(Long projectId, FeatureListQuery query) {
        Long result = queryFactory
                .select(qp.count())
                .from(qp)
                .where(buildPalmWhere(projectId, query))
                .fetchOne();
        return result != null ? result : 0L;
    }

    private BooleanBuilder buildFaceWhere(Long projectId, FeatureListQuery query) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(qf.project.id.eq(projectId));
        if (query.isDeleted() != null) {
            b.and(qf.isDeleted.eq(query.isDeleted()));
        } else {
            b.and(qf.isDeleted.eq(false));
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            BooleanBuilder kw = new BooleanBuilder();
            kw.or(qf.featureId.containsIgnoreCase(query.keyword()));
            kw.or(qf.description.containsIgnoreCase(query.keyword()));
            b.and(kw);
        }
        if (query.hasDate()) {
            b.and(qf.createdAt.goe(query.startDateTime()));
            b.and(qf.createdAt.loe(query.endDateTime()));
        }
        return b;
    }

    private BooleanBuilder buildPalmWhere(Long projectId, FeatureListQuery query) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(qp.project.id.eq(projectId));
        if (query.isDeleted() != null) {
            b.and(qp.isDeleted.eq(query.isDeleted()));
        } else {
            b.and(qp.isDeleted.eq(false));
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            BooleanBuilder kw = new BooleanBuilder();
            kw.or(qp.featureId.containsIgnoreCase(query.keyword()));
            kw.or(qp.description.containsIgnoreCase(query.keyword()));
            b.and(kw);
        }
        if (query.hasDate()) {
            b.and(qp.createdAt.goe(query.startDateTime()));
            b.and(qp.createdAt.loe(query.endDateTime()));
        }
        return b;
    }
}
