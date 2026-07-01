package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BiometricFeatureJpaRepository extends JpaRepository<BiometricFeature, Long> {

    Optional<BiometricFeature> findByIdAndTypeAndIsDeletedFalse(Long id, FeatureType type);

    Optional<BiometricFeature> findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
            String featureId, Long projectId, FeatureType type);

    long countByProjectIdAndTypeAndIsDeletedFalse(Long projectId, FeatureType type);
}
