package ai.univs.gate.support.privacy;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 삭제한 특징점의 원본 이미지 파기 전용 조회·갱신 (UG-347).
 *
 * <p>{@link ProjectPurgeRepository} 와 같은 이유로 기존 리포지토리와 나눈다 — 제품 경로는 전부 {@code is_deleted = false}
 * 를 보고, 여기는 {@code true} 인 것만 본다.
 *
 * <p><b>{@code feature_image_path IS NULL} 이 「파일을 지웠다」는 표시다.</b> 파일을 지운 뒤 경로를 비우므로 정리 대상
 * 조회의 종료 조건이 되고, 마이그레이션 없이 기존 삭제분도 같은 조회로 잡힌다. 행 자체는 남긴다 — 등록·삭제 이력과
 * 대시보드 집계가 이 행을 가리킨다.
 */
@Repository
@RequiredArgsConstructor
public class DeletedFeatureImagePurgeRepository {

    private final EntityManager em;

    /**
     * 삭제됐는데 이미지 경로가 남은 특징점 id 를 {@code afterId} 다음부터. 커서로 넘기는 이유는 계속 실패하는 건(권한·볼륨
     * 문제)이 맨 앞에 있으면 처음부터 고르는 조회는 그 건들이 실행당 상한을 영구히 차지하기 때문이다.
     */
    public List<Long> findTargetIds(long afterId, int limit) {
        return em.createQuery("""
                        SELECT f.id FROM BiometricFeature f
                         WHERE f.isDeleted = true AND f.featureImagePath IS NOT NULL AND f.id > :afterId
                         ORDER BY f.id
                        """, Long.class)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    /** 삭제된 특징점의 남은 이미지 경로. 아직 살아 있거나 이미 비웠으면 비어 있다. */
    public Optional<String> findImagePathOfDeleted(Long featureSeq) {
        return em.createQuery("""
                        SELECT f.featureImagePath FROM BiometricFeature f
                         WHERE f.id = :id AND f.isDeleted = true AND f.featureImagePath IS NOT NULL
                        """, String.class)
                .setParameter("id", featureSeq)
                .getResultStream()
                .findFirst();
    }

    /**
     * 살아 있는 특징점이 같은 파일을 가리키는가. 지금 등록 경로는 요청마다 새 파일을 만들어 겹치지 않지만, 겹치면 지우는
     * 순간 살아 있는 특징점의 원본이 사라진다 — 되돌릴 수 없으므로 한 번 더 본다.
     */
    public boolean isReferencedByLiveFeature(String path) {
        return em.createQuery("""
                        SELECT COUNT(f) FROM BiometricFeature f
                         WHERE f.featureImagePath = :path AND f.isDeleted = false
                        """, Long.class)
                .setParameter("path", path)
                .getSingleResult() > 0;
    }

    /**
     * 경로를 비운다. 읽은 경로와 같을 때만 — 즉시 파기와 정기 정리가 겹쳐도 한쪽만 1행을 얻고, 결과는 같다.
     *
     * @return 갱신한 행 수 (0 또는 1)
     */
    public int clearImagePath(Long featureSeq, String path) {
        return em.createQuery("""
                        UPDATE BiometricFeature f SET f.featureImagePath = NULL
                         WHERE f.id = :id AND f.isDeleted = true AND f.featureImagePath = :path
                        """)
                .setParameter("id", featureSeq)
                .setParameter("path", path)
                .executeUpdate();
    }
}
