package ai.univs.gate.support.privacy;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.project.domain.entity.Project;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 프로젝트 데이터 정리 전용 조회·삭제 (UG-303).
 *
 * <p>기존 리포지토리에 넣지 않은 이유는 여기 쿼리들이 <b>정반대 조건</b>을 보기 때문이다.
 * 제품 경로의 조회는 전부 {@code is_deleted = false} 를 걸고, 여기는 {@code true} 인 것만
 * 찾는다. 같은 인터페이스에 두면 그 조건을 빠뜨린 메서드가 제품 경로에서 잘못 집어질 수 있다.
 *
 * <p>물리 삭제({@code remove})도 이 클래스에만 있다. 나머지 코드에서는 소프트 삭제만 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class ProjectPurgeRepository {

    private final EntityManager em;

    /**
     * 유예가 지난 삭제 프로젝트 id.
     *
     * <p>{@code deleted_at IS NULL} 은 제외한다 — V32 이전에 삭제된 행은 삭제 시각을 알 수 없다.
     * 임의로 채워 지우는 것보다 남기는 쪽이 안전하다.
     *
     * <p>id 만 가져오는 이유는 각 프로젝트를 <b>별도 트랜잭션</b>에서 처리하기 때문이다.
     * 엔티티를 들고 나가면 그 트랜잭션이 끝난 뒤 준영속 상태로 쓰이게 된다.
     */
    public List<Long> findPurgeTargetIds(LocalDateTime deletedBefore, int limit) {
        return em.createQuery("""
                        SELECT p.id FROM Project p
                         WHERE p.isDeleted = true
                           AND p.deletedAt IS NOT NULL
                           AND p.deletedAt < :cutoff
                         ORDER BY p.deletedAt ASC
                        """, Long.class)
                .setParameter("cutoff", deletedBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    /** 삭제된 프로젝트만 찾는다 — 정리 중에 복구된 프로젝트를 건드리지 않기 위한 재확인이다. */
    public Optional<Project> findDeletedProject(Long projectId) {
        return em.createQuery("""
                        SELECT p FROM Project p
                         WHERE p.id = :id AND p.isDeleted = true
                        """, Project.class)
                .setParameter("id", projectId)
                .getResultStream()
                .findFirst();
    }

    /**
     * 그 프로젝트의 생체 특징점 전부.
     *
     * <p><b>{@code is_deleted} 를 보지 않는다.</b> 소프트 삭제된 특징점도 행과 이미지가 남아
     * 있으므로 정리 대상이다 — 오히려 그쪽이 더 오래 방치된 데이터다.
     */
    public List<BiometricFeature> findFeaturesOf(Long projectId) {
        return em.createQuery("""
                        SELECT f FROM BiometricFeature f
                         WHERE f.project.id = :projectId
                         ORDER BY f.id ASC
                        """, BiometricFeature.class)
                .setParameter("projectId", projectId)
                .getResultList();
    }

    /** 물리 삭제. 이 클래스 밖에서는 쓰지 않는다. */
    public void deleteFeature(BiometricFeature feature) {
        em.remove(em.contains(feature) ? feature : em.merge(feature));
    }
}
