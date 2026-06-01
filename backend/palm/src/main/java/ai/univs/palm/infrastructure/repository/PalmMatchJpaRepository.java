package ai.univs.palm.infrastructure.repository;

import ai.univs.palm.domain.PalmMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PalmMatchJpaRepository extends JpaRepository<PalmMatch, Long> {

}
