package ai.univs.face.infrastructure.repository;

import ai.univs.face.domain.FaceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaceHistoryJpaRepository extends JpaRepository<FaceHistory, Long> {

}
