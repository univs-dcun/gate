package ai.univs.face.infrastructure.repository;

import ai.univs.face.domain.FaceMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaceMatchJpaRepository extends JpaRepository<FaceMatch, Long> {

}
