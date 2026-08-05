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
 * {@code type} 컬럼으로만 갈린다. 세 조회 메서드의 차이는 {@code type} 조건 하나뿐이므로, 그 조건을
 * 빼면 통합 조회가 되고 offset/limit 을 그대로 DB 에 넘길 수 있다 (UG-269).
 *
 * <p><b>정렬은 통합 조회에서만 달라진다.</b> 각 쿼리의 {@code ORDER BY} 는 예전부터
 * {@code createdAt DESC, id DESC} 였지만, 통합 목록의 <i>실효</i> 순서는 그렇지 않았다 — 자바에서
 * {@code createdAt} 만 비교해 병합했고 같은 값이면 face 를 먼저 놓았다. 지금은 DB 가
 * {@code createdAt DESC, id DESC} 로 정렬하므로 <b>{@code createdAt} 이 완전히 같은 행이 타입 간에
 * 있으면 어느 페이지에 실리는지가 바뀔 수 있다.</b> 단일 타입 조회와 같은 규칙이라 이쪽이
 * 일관적이지만, 순서 안의 차이가 아니라 페이지 구성의 차이임을 알고 있어야 한다. V21 이
 * {@code created_at} 을 그대로 옮겼으므로 이관된 데이터에서는 값이 겹칠 수 있다.
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
     * <p>단일 쿼리는 offset/limit 을 DB 가 처리하므로 <b>자바 힙</b> 적재량이 페이지 크기에
     * 비례한다. DB 쪽 비용은 그대로다 — {@code (project_id, is_deleted, created_at DESC, id DESC)}
     * 를 덮는 인덱스가 없어 여전히 프로젝트 행 전체를 정렬한 뒤 앞부분을 버린다. 예전 경로는
     * 그 정렬을 두 번 하고 최대 200만 개 객체까지 만들었으므로 개선은 맞지만, 인덱스는
     * 별건(UG-282)이다.
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
        } else {
            // UG-269 반박 리뷰: type 조건을 통째로 빼지 않고 값을 열거한다. 이유가 둘이다.
            //
            // 1) type 은 VARCHAR(10) 이고 CHECK 제약이 없다. enum 에 없는 값이 한 행이라도 있으면
            //    (부분 적용된 마이그레이션, 온프레미스 수동 수정) Hibernate 가 변환에 실패해
            //    목록 API 전체가 500 이 된다. 조건이 있으면 그 행은 예전처럼 제외된다.
            // 2) FeatureType 에 값이 추가되면 ALL 이 조용히 "전부" 로 넓어진다. switch 는
            //    FeatureQueryType(FACE/PALM/ALL) 을 대상으로 하므로 컴파일 에러가 나지 않는다.
            //    여기서 열거하면 그때 이 줄을 고칠지 판단하게 된다.
            b.and(bf.type.in(FeatureType.FACE, FeatureType.PALM));
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
