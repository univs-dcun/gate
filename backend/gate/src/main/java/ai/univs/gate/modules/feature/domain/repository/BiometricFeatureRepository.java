package ai.univs.gate.modules.feature.domain.repository;

import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface BiometricFeatureRepository {

    BiometricFeature save(BiometricFeature biometricFeature);

    Optional<BiometricFeature> findByIdAndTypeAndIsDeletedFalse(Long id, FeatureType type);

    Optional<BiometricFeature> findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
            String featureId, Long projectId, FeatureType type);

    Page<BiometricFeature> findAllByQuery(BiometricFeatureQuery query, Long projectId);

    long countByProjectIdAndTypeAndIsDeletedFalse(Long projectId, FeatureType type);
}
