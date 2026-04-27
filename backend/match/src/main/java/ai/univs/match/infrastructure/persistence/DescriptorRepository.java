package ai.univs.match.infrastructure.persistence;

import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DescriptorRepository extends JpaRepository<Descriptor, Long> {

    Optional<Descriptor> findByFaceIdAndBranch(String faceId, Branch branch);

    int countByBranch(Branch branch);

    int countByBranchAndDescriptorVersion(Branch branch, int version);
}
