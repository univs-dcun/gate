package ai.univs.palm.infrastructure.repository;

import ai.univs.palm.domain.PalmLiveness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PalmLivenessJpaRepository extends JpaRepository<PalmLiveness, Long> {

}
