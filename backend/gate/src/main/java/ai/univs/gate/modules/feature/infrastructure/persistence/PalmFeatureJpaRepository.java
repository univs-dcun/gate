package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PalmFeatureJpaRepository extends JpaRepository<PalmFeature, Long> {

    Optional<PalmFeature> findByIdAndIsDeletedFalse(Long id);

    Optional<PalmFeature> findByFeatureIdAndProjectIdAndIsDeletedFalse(String featureId, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}
