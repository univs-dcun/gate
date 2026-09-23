package ai.univs.gate.support.privacy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * 정리 대상 인증 이력 — id 와 그 행이 들고 있는 두 이미지 경로.
     *
     * <p>엔티티를 통째로 들고 오지 않는 이유는 두 가지다. 배치 크기만큼의 영속성 컨텍스트가
     * 쌓이지 않고, 아래 벌크 삭제가 그 컨텍스트와 어긋날 여지도 없다.
     *
     * <p><b>{@code match_type} 으로 거르지 않는다.</b> 초판은 {@code REGISTER} 잔존 행을
     * 쿼리에서 뺐는데(그 행은 {@code matched_feature_image_path} 에 등록 이미지를 담고 있다),
     * 그러면 행 자체가 영구 면제된다 — 개인정보를 파기하는 기능이 특정 행을 무기한 보유하는
     * 셈이다. 파일을 지킬 책임은 {@link HistoryPurgeService} 의 참조 검사로 옮겼고, 행은
     * 종류를 가리지 않고 보존 기간이 지나면 지운다.
     *
     * <p>정렬 기준이 {@code created_at} 인 이유는 V33 인덱스를 타기 위해서다. 대상이 0건일 때
     * 첫 엔트리에서 끝난다.
     */
    public List<MatchHistoryPurgeTarget> findMatchHistoryToPurge(
            LocalDateTime createdBefore, int limit) {
        return em.createQuery("""
                        SELECT new ai.univs.gate.support.privacy.MatchHistoryPurgeTarget(
                                   h.id, h.matchedFeatureImagePath, h.featureImagePath)
                          FROM MatchHistory h
                         WHERE h.createdAt < :cutoff
                         ORDER BY h.createdAt ASC
                        """, MatchHistoryPurgeTarget.class)
                .setParameter("cutoff", createdBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * 정리 대상 특징점 사건 이력 — id 와 이미지 경로.
     *
     * <p>{@code feature_history} 에는 프로브 이미지 컬럼이 없고, 가진 경로는 등록된 특징점에서
     * 복사된 값이다. 즉 지금은 지울 것이 없는 셈인데, <b>그래도 같은 참조 검사를 거친다.</b>
     * 여기만 "복사된 값이니 안전하다" 로 특별 취급하면, 자기 이미지를 올리는 사건 종류가
     * 추가되는 순간 조용히 영구 고아가 생긴다 — 이 티켓에서 두 번 겪은 실패 모드다.
     */
    public List<MatchHistoryPurgeTarget> findFeatureHistoryToPurge(
            LocalDateTime createdBefore, int limit) {
        return em.createQuery("""
                        SELECT new ai.univs.gate.support.privacy.MatchHistoryPurgeTarget(
                                   h.id, NULL, h.featureImagePath)
                          FROM FeatureHistory h
                         WHERE h.createdAt < :cutoff
                         ORDER BY h.createdAt ASC
                        """, MatchHistoryPurgeTarget.class)
                .setParameter("cutoff", createdBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * 한 번의 {@code IN} 에 넣는 경로 수 상한.
     *
     * <p><b>오라클 때문이다.</b> Oracle 19c(온프레미스 납품 대상)는 {@code IN} 리스트가 1000 을
     * 넘으면 {@code ORA-01795} 로 거절한다. 배치 500행 × 이미지 경로 컬럼 2개 = <b>정확히
     * 1000</b> 이라 오늘은 통과하지만 여유가 0 이다. 배치를 키우거나 이력에 세 번째 경로
     * 컬럼이 생기는 순간 오라클 납품처에서 매일 밤 터지고, H2 슬라이스는 영원히 초록이다
     * (3차 반박 리뷰 지적).
     *
     * <p>Hibernate 의 {@code in_clause_parameter_padding} 이 켜지면 1000 이 1024 로 패딩돼
     * 그것만으로도 넘는다. 지금은 꺼져 있지만 그 기본값에 안전을 맡기지 않는다.
     *
     * <p>그래서 쪼갠다. 상한과 배치 크기의 결합 자체를 없애는 편이 주석으로 경고하는 것보다
     * 낫다.
     */
    private static final int IN_절_상한 = 500;

    /**
     * 주어진 경로 중 <b>살아 있는 특징점이 아직 가리키는</b> 것.
     *
     * <p>이 결과가 삭제 금지 목록이다. 나머지는 이 이력 행 말고는 아무도 가리키지 않으므로,
     * 행과 함께 지워야 한다.
     *
     * <p>{@code is_deleted} 를 보지 않는 이유는 소프트 삭제된 특징점도 행과 파일을 모두 들고
     * 있기 때문이다({@code DeleteFaceFeatureUseCase} 는 플래그만 찍고 파일은 손대지 않는다).
     * 그 파일을 여기서 지우면 살아 있는 특징점 행이 깨진 경로를 가리키게 된다 — 지우는 것은
     * {@link ProjectDataPurgeService} 가 특징점과 함께 할 일이다.
     *
     * <p>인덱스는 두지 않았다. 이 조회는 밤에 배치당 한 번 돌고 실행당 최대 40회다. 등록
     * 특징점이 수백만 행인 납품에서도 야간 수십 초 수준이라 판단했다 — 꺼져 있는 기능을 위해
     * 등록 경로에 쓰기 비용을 얹지 않는다. 실측해서 문제가 되면 그때 넣는다.
     */
    public Set<String> findPathsStillReferencedByFeatures(Collection<String> paths) {
        List<String> 남은것 = List.copyOf(paths);
        Set<String> referenced = new HashSet<>();

        for (int from = 0; from < 남은것.size(); from += IN_절_상한) {
            referenced.addAll(em.createQuery("""
                            SELECT DISTINCT f.featureImagePath FROM BiometricFeature f
                             WHERE f.featureImagePath IN :paths
                            """, String.class)
                    .setParameter("paths", 남은것.subList(from, Math.min(from + IN_절_상한, 남은것.size())))
                    .getResultList());
        }
        return referenced;
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
