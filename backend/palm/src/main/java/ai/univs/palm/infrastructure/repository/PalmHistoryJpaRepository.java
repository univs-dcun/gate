package ai.univs.palm.infrastructure.repository;

import ai.univs.palm.domain.PalmHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PalmHistoryJpaRepository extends JpaRepository<PalmHistory, Long> {

}
