package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchPostgresqlProjection;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import jakarta.persistence.EntityManager;
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
}
