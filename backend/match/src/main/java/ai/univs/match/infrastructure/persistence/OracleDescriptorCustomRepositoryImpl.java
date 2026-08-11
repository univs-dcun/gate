package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchOracleProjection;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import jakarta.persistence.EntityManager;
import java.util.List;
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
}
