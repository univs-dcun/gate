package ai.univs.face.infrastructure.repository;

import ai.univs.face.domain.FaceLiveness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaceLivenessJpaRepository extends JpaRepository<FaceLiveness, Long> {

}
