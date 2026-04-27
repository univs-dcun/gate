package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchOracleProjection;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import jakarta.persistence.EntityManager;
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
}
