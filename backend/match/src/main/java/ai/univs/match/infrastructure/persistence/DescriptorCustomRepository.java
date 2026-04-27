package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import org.springframework.stereotype.Repository;

@Repository
public interface DescriptorCustomRepository {

    Double oneToOneMatch(byte[] requestDescriptor, byte[] target, int version);

    MatchResultProjection oneToManyMatch(Long branchId, byte[] requestDescriptor, int version);
}
