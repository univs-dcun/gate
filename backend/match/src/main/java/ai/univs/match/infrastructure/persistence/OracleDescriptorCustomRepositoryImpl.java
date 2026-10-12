package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchOracleProjection;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;

@Profile("oracle")
@RequiredArgsConstructor
public class OracleDescriptorCustomRepositoryImpl implements DescriptorCustomRepository {

    private final EntityManager em;

    @Override
    public Double oneToOneMatch(byte[] descriptorBody, byte[] targetDescriptorBody, int version) {
        String sql = """
            SELECT
                vlmatch(:descriptorBody, :targetDescriptorBody, :version)
            FROM dual
        """;

        Number result = (Number) em.createNativeQuery(sql)
                .setParameter("descriptorBody", descriptorBody)
                .setParameter("targetDescriptorBody", targetDescriptorBody)
                .setParameter("version", version)
                .getSingleResult();

        return result.doubleValue();
    }

    @Override
    public MatchResultProjection oneToManyMatch(Long branchId, byte[] descriptorBody, int version) {
        String sql = """
            SELECT /*+ parallel(20) */
                   faceId,
                   distance
            FROM (
                SELECT d.face_id AS faceId,
                       vlmatch(:descriptorBody, d.descriptor_body, :version) AS distance
                FROM descriptor d
                WHERE d.branch_id = :branchId
                ORDER BY distance ASC
            )
            WHERE ROWNUM = 1
        """;

        var projection = (MatchOracleProjection) em.createNativeQuery(sql, MatchOracleProjection.class)
                .setParameter("branchId", branchId)
                .setParameter("descriptorBody", descriptorBody)
                .setParameter("version", version)
                .getSingleResult();

        return new MatchResultProjection(projection);
    }

    /**
     * UG-314. 위 {@link #oneToManyMatch} 와 같은 모양이고 {@code ROWNUM = 1} 이
     * {@code ROWNUM <= :limit} 로 바뀐다.
     *
     * <p>{@code ROWNUM} 은 인라인 뷰 <b>바깥</b>에 있어야 한다. 안쪽에 두면 정렬 전에 번호가
     * 매겨져 "아무 k건" 이 나온다. 기존 쿼리가 이미 그 형태라 그대로 따른다.
     *
     * <p>정렬에 {@code faceId} 를 덧붙인 이유는 postgresql 쪽과 같다 — 거리 동률일 때 순서가
     * 실행 계획에 좌우되면 같은 요청이 매번 다른 후보를 돌려준다.
     */
    @Override
    public List<MatchResultProjection> oneToManyMatchTopK(
            Long branchId, byte[] descriptorBody, int version, int limit) {
        String sql = """
            SELECT /*+ parallel(20) */
                   faceId,
                   distance
            FROM (
                SELECT d.face_id AS faceId,
                       vlmatch(:descriptorBody, d.descriptor_body, :version) AS distance
                FROM descriptor d
                WHERE d.branch_id = :branchId
                ORDER BY distance ASC, faceId ASC
            )
            WHERE ROWNUM <= :limit
        """;

        @SuppressWarnings("unchecked")
        List<MatchOracleProjection> projections =
                em.createNativeQuery(sql, MatchOracleProjection.class)
                        .setParameter("branchId", branchId)
                        .setParameter("descriptorBody", descriptorBody)
                        .setParameter("version", version)
                        .setParameter("limit", limit)
                        .getResultList();

        return projections.stream().map(MatchResultProjection::new).toList();
    }

    /** UG-374. Oracle 은 자식 INSERT 가 부모 행을 잠그지 않아 {@code FOR UPDATE} 로 충분하다. */
    @Override
    public void lockBranchForRecheck(Long branchId) {
        em.createNativeQuery("SELECT branch_id FROM branch WHERE branch_id = :branchId FOR UPDATE")
                .setParameter("branchId", branchId)
                .getResultList();
    }

    /**
     * UG-374. {@code ROWNUM} 은 정렬된 인라인 뷰 바깥에 둔다(UG-314 와 같은 이유). parallel 힌트는 두지 않는다 — 최근
     * 몇 건만 인덱스로 읽는다.
     */
    @Override
    public Optional<MatchResultProjection> nearestRecent(Long branchId,
                                                         Long excludeDescriptorId,
                                                         LocalDateTime since,
                                                         int descriptorVersion,
                                                         byte[] descriptorBody,
                                                         int version) {
        String sql = """
            SELECT faceId,
                   distance
            FROM (
                SELECT d.face_id AS faceId,
                       vlmatch(:descriptorBody, d.descriptor_body, :version) AS distance
                FROM descriptor d
                WHERE d.branch_id = :branchId
                  AND d.created_at >= :since
                  AND d.descriptor_id <> :excludeDescriptorId
                  AND d.descriptor_version = :descriptorVersion
                ORDER BY distance ASC, faceId ASC
            )
            WHERE ROWNUM = 1
        """;

        @SuppressWarnings("unchecked")
        List<MatchOracleProjection> projections =
                em.createNativeQuery(sql, MatchOracleProjection.class)
                        .setParameter("branchId", branchId)
                        .setParameter("since", since)
                        .setParameter("excludeDescriptorId", excludeDescriptorId)
                        .setParameter("descriptorVersion", descriptorVersion)
                        .setParameter("descriptorBody", descriptorBody)
                        .setParameter("version", version)
                        .getResultList();

        return projections.stream().findFirst().map(MatchResultProjection::new);
    }
}
