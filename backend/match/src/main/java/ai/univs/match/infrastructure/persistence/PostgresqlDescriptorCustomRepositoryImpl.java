package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchPostgresqlProjection;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;

@Profile("postgresql")
@RequiredArgsConstructor
public class PostgresqlDescriptorCustomRepositoryImpl implements DescriptorCustomRepository {

    private final EntityManager em;

    @Override
    public Double oneToOneMatch(byte[] descriptorBody, byte[] targetDescriptorBody, int version) {
        String sql = """
            SELECT
                *
            FROM vlmatch(:descriptorBody, :targetDescriptorBody, :version)
        """;
        return ((Number) em.createNativeQuery(sql)
                .setParameter("descriptorBody", descriptorBody)
                .setParameter("targetDescriptorBody", targetDescriptorBody)
                .setParameter("version", version)
                .getSingleResult()).doubleValue();
    }

    @Override
    public MatchResultProjection oneToManyMatch(Long branchId, byte[] descriptorBody, int version) {
        String sql = """
            SELECT
                d.face_id AS faceId,
                CAST(vlmatch(:descriptorBody, d.descriptor_body, :version) AS double precision) AS distance
            FROM descriptor d
            where d.branch_id = :branchId
            ORDER BY distance ASC
            LIMIT 1
        """;

        var projection = (MatchPostgresqlProjection) em.createNativeQuery(sql, MatchPostgresqlProjection.class)
                .setParameter("branchId", branchId)
                .setParameter("descriptorBody", descriptorBody)
                .setParameter("version", version)
                .getSingleResult();

        return new MatchResultProjection(projection);
    }

    /**
     * UG-314. 위 {@link #oneToManyMatch} 와 같은 쿼리에 {@code LIMIT} 만 다르다.
     *
     * <p>정렬에 {@code face_id} 를 덧붙인 것은 <b>동률 때문</b>이다. 거리만으로 정렬하면 값이
     * 같은 행들의 순서를 DB 가 정하고, 같은 요청이 실행 계획에 따라 다른 목록을 돌려줄 수 있다.
     * 1건만 쓰던 기존 쿼리에서는 잘 드러나지 않던 문제가 k건을 자르는 순간 "경계에 걸친 후보가
     * 매번 바뀐다" 로 나타난다.
     */
    @Override
    public List<MatchResultProjection> oneToManyMatchTopK(
            Long branchId, byte[] descriptorBody, int version, int limit) {
        String sql = """
            SELECT
                d.face_id AS faceId,
                CAST(vlmatch(:descriptorBody, d.descriptor_body, :version) AS double precision) AS distance
            FROM descriptor d
            where d.branch_id = :branchId
            ORDER BY distance ASC, d.face_id ASC
            LIMIT :limit
        """;

        @SuppressWarnings("unchecked")
        List<MatchPostgresqlProjection> projections =
                em.createNativeQuery(sql, MatchPostgresqlProjection.class)
                        .setParameter("branchId", branchId)
                        .setParameter("descriptorBody", descriptorBody)
                        .setParameter("version", version)
                        .setParameter("limit", limit)
                        .getResultList();

        return projections.stream().map(MatchResultProjection::new).toList();
    }
}
