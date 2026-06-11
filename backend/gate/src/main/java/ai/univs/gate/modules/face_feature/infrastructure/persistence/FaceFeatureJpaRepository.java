package ai.univs.gate.modules.face_feature.infrastructure.persistence;

import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaceFeatureJpaRepository extends JpaRepository<FaceFeature, Long> {

    Optional<FaceFeature> findByIdAndIsDeleted(Long id, boolean isDeleted);

    Optional<FaceFeature> findByFeatureIdAndProjectIdAndIsDeleted(String featureId, Long projectId, boolean isDeleted);

    long countByProjectIdAndIsDeleted(Long projectId, boolean isDeleted);
}
