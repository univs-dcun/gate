package ai.univs.gate.support.privacy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 이력 보존 정리 전용 조회·삭제 (UG-282).
 *
 * <p>{@link ProjectPurgeRepository} 와 같은 이유로 제품 리포지토리와 분리했다 — 여기 쿼리는
 * 프로젝트 소유나 {@code is_deleted} 를 보지 않고 <b>시간만</b> 본다. 제품 경로에서 잘못
 * 집히면 전 고객의 이력을 건드리는 쿼리가 된다.
 *
 * <p>물리 삭제는 이 클래스에만 있다.
 */
@Repository
@RequiredArgsConstructor
public class HistoryPurgeRepository {

    private final EntityManager em;

    /**
     * 정리 대상 인증 이력 — id, {@code match_type}, 그리고 두 이미지 경로.
     *
     * <p>엔티티를 통째로 들고 오지 않는 이유는 두 가지다. 배치 크기만큼의 영속성 컨텍스트가
     * 쌓이지 않고, 아래 벌크 삭제가 그 컨텍스트와 어긋날 여지도 없다.
     *
     * <p><b>두 경로를 다 가져오되, 지울 것은 {@link MatchHistoryPurgeTarget#ownedImagePaths()}
     * 가 고른다.</b> 초판은 {@code feature_image_path} 를 아예 조회하지 않는 것으로 사고를
     * 막으려 했는데, 그 컬럼이 이 행만의 것인 경우가 실제로 있었다(VERIFY_IMAGE 의 신분증
     * 이미지). 가져오지 않으면 그 파일이 영구 고아가 된다. 판단은 레코드 한 곳에 둔다.
     *
     * <p><b>{@code REGISTER} 은 제외한다</b> (반박 리뷰 지적). 등록은 이제
     * {@code feature_history} 의 사건이고 V27 이 {@code match_history} 의 REGISTER 행을
     * 지웠다 — 다만 짝이 없는 행은 <b>일부러 남겼다</b>(V27 주석). 그 잔존 행은
     * {@code matched_feature_image_path} 에 <b>등록 이미지</b>를 담고 있어서
     * (V26 주석: 등록 행은 경로를 먼저 그쪽에 넣었다), 여기서 집으면
     * <b>살아 있는 특징점의 사진을 지운다.</b> {@code ActivityLog} 의 {@code @Subselect} 와
     * {@code MatchType} enum 이 이미 같은 잔존 행을 방어하고 있다 — 읽기만 막아 두고 유일하게
     * 파괴적인 이 경로를 비워 둘 이유가 없다.
     *
     * <p>정렬 기준이 {@code created_at} 인 이유는 V33 인덱스를 타기 위해서다. 대상이 0건일 때
     * 첫 엔트리에서 끝난다.
     */
    public List<MatchHistoryPurgeTarget> findMatchHistoryToPurge(
            LocalDateTime createdBefore, int limit) {
        return em.createQuery("""
                        SELECT new ai.univs.gate.support.privacy.MatchHistoryPurgeTarget(
                                   h.id, h.matchType,
                                   h.matchedFeatureImagePath, h.featureImagePath)
                          FROM MatchHistory h
                         WHERE h.createdAt < :cutoff
                           AND h.matchType <> ai.univs.gate.modules.feature.domain.enums.MatchType.REGISTER
                         ORDER BY h.createdAt ASC
                        """, MatchHistoryPurgeTarget.class)
                .setParameter("cutoff", createdBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * 정리 대상 특징점 사건 이력 — id 만 가져온다.
     *
     * <p>{@code feature_history} 에는 프로브 이미지 컬럼이 없다. 가진
     * {@code feature_image_path} 는 위와 같은 이유로 지우면 안 되는 공유 경로다. 즉 이쪽은
     * 행만 지운다.
     */
    public List<Long> findFeatureHistoryToPurge(LocalDateTime createdBefore, int limit) {
        return em.createQuery("""
                        SELECT h.id FROM FeatureHistory h
                         WHERE h.createdAt < :cutoff
                         ORDER BY h.createdAt ASC
                        """, Long.class)
                .setParameter("cutoff", createdBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    /** 물리 삭제. 이 클래스 밖에서는 쓰지 않는다. */
    public int deleteMatchHistory(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return em.createQuery("DELETE FROM MatchHistory h WHERE h.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }

    /** 물리 삭제. 이 클래스 밖에서는 쓰지 않는다. */
    public int deleteFeatureHistory(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return em.createQuery("DELETE FROM FeatureHistory h WHERE h.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }
}
