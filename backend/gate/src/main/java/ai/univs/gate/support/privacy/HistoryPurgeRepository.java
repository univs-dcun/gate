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
     * 정리 대상 인증 이력 — id 와 그 시도에서 <b>올린 이미지 경로</b>만 가져온다.
     *
     * <p>엔티티를 통째로 들고 오지 않는 이유는 두 가지다. 배치 크기만큼의 영속성 컨텍스트가
     * 쌓이지 않고, 아래 벌크 삭제가 그 컨텍스트와 어긋날 여지도 없다.
     *
     * <p><b>{@code feature_image_path} 는 가져오지 않는다.</b> 그 컬럼은 등록된 특징점의
     * 이미지 경로를 <b>복사해 둔 값</b>이라 살아 있는 {@code biometric_feature} 및 다른 이력
     * 행과 같은 파일을 가리킨다. 이력을 지우면서 그 파일을 지우면 <b>등록된 사용자의 사진이
     * 사라진다.</b> 반면 {@code matched_feature_image_path} 는 그 시도에서 올린 프로브
     * 이미지이고 매 요청마다 새 UUID 로 저장된다({@code FileUtil}) — 이 행 말고는 아무도
     * 가리키지 않는다.
     *
     * <p>정렬 기준이 {@code created_at} 인 이유는 V33 인덱스를 타기 위해서다. 대상이 0건일 때
     * 첫 엔트리에서 끝난다.
     */
    public List<MatchHistoryPurgeTarget> findMatchHistoryToPurge(
            LocalDateTime createdBefore, int limit) {
        return em.createQuery("""
                        SELECT new ai.univs.gate.support.privacy.MatchHistoryPurgeTarget(
                                   h.id, h.matchedFeatureImagePath)
                          FROM MatchHistory h
                         WHERE h.createdAt < :cutoff
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
