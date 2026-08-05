package ai.univs.gate.facade.feature.infrastructure.persistence;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.modules.feature.domain.entity.QBiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 특징점 통합 목록 조회 전용 리포지토리.
 *
 * <p>face·palm 은 서로 다른 테이블이 아니라 <b>같은 {@code biometric_feature} 테이블</b>이고
 * {@code type} 컬럼으로만 갈린다. 세 조회 메서드의 차이는 {@code type} 조건 하나뿐이며, 정렬 기준
 * ({@code createdAt DESC, id DESC})도 동일하다. 그래서 {@code type} 조건을 빼면 통합 조회가 되고,
 * offset/limit 을 그대로 DB 에 넘길 수 있다 (UG-269).
 */
@Repository
public class FeatureDSLRepository {

    private final JPAQueryFactory queryFactory;
    private final QBiometricFeature bf = QBiometricFeature.biometricFeature;

    public FeatureDSLRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public List<FeatureRow> findFaceRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return findRows(projectId, query, FeatureType.FACE, offset, limit);
    }

    public long countFace(Long projectId, FeatureListQuery query) {
        return count(projectId, query, FeatureType.FACE);
    }

    public List<FeatureRow> findPalmRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return findRows(projectId, query, FeatureType.PALM, offset, limit);
    }

    public long countPalm(Long projectId, FeatureListQuery query) {
        return count(projectId, query, FeatureType.PALM);
    }

    /**
     * type 조건 없이 전체를 한 번에 읽는다 (UG-269).
     *
     * <p>예전에는 face·palm 을 각각 {@code 0..offset+pageSize} 까지 읽어 자바 힙에서 병합한 뒤 한
     * 페이지만 잘라냈다. 상한이 {@code page<=1000, pageSize<=1000} 이므로 최악의 경우 한 요청이
     * 200만 행을 적재했다 — 인증된 사용자 1명이 힙을 고갈시킬 수 있었다.
     *
     * <p>단일 쿼리는 offset/limit 을 DB 가 처리하므로 적재량이 페이지 크기에 비례한다.
     */
    public List<FeatureRow> findAllRows(Long projectId, FeatureListQuery query, long offset, int limit) {
        return findRows(projectId, query, null, offset, limit);
    }

    public long countAll(Long projectId, FeatureListQuery query) {
        return count(projectId, query, null);
    }

    /**
     * {@code featureType} 이 {@code null} 이면 type 조건을 걸지 않는다 (통합 조회).
     *
     * <p>{@code type} 을 상수가 아니라 {@code bf.type} 으로 select 한다. 통합 조회는 행마다 값이
     * 다르므로 상수를 쓸 수 없고, 단일 타입 조회도 어차피 그 값으로 필터링하므로 결과가 같다.
     * 덕분에 세 경로가 완전히 같은 쿼리 모양을 공유한다.
     */
    private List<FeatureRow> findRows(Long projectId,
                                      FeatureListQuery query,
                                      FeatureType featureType,
                                      long offset,
                                      int limit
    ) {
        return queryFactory
                .select(Projections.constructor(FeatureRow.class,
                        bf.type,
                        bf.id,
                        bf.description,
                        bf.featureImagePath,
                        bf.featureId,
                        bf.createdAt))
                .from(bf)
                .where(buildWhere(projectId, query, featureType))
                .orderBy(bf.createdAt.desc(), bf.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    private long count(Long projectId, FeatureListQuery query, FeatureType featureType) {
        Long result = queryFactory
                .select(bf.count())
                .from(bf)
                .where(buildWhere(projectId, query, featureType))
                .fetchOne();
        return result != null ? result : 0L;
    }

    private BooleanBuilder buildWhere(Long projectId, FeatureListQuery query, FeatureType featureType) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(bf.project.id.eq(projectId));
        if (featureType != null) {
            b.and(bf.type.eq(featureType));
        }
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
