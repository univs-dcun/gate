package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BiometricFeatureRepositoryImpl implements BiometricFeatureRepository {

    private final BiometricFeatureJpaRepository jpaRepository;
    private final BiometricFeatureDSLRepository dslRepository;

    @Override
    public BiometricFeature save(BiometricFeature biometricFeature) {
        return jpaRepository.save(biometricFeature);
    }

    @Override
    public Optional<BiometricFeature> findByIdAndTypeAndIsDeletedFalse(Long id, FeatureType type) {
        return jpaRepository.findByIdAndTypeAndIsDeletedFalse(id, type);
    }

    @Override
    public Optional<BiometricFeature> findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
            String featureId, Long projectId, FeatureType type) {
        return jpaRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(featureId, projectId, type);
    }

    @Override
    public Page<BiometricFeature> findAllByQuery(BiometricFeatureQuery query, Long projectId) {
        return dslRepository.findAllByQuery(query, projectId);
    }

    @Override
    public long countByProjectIdAndTypeAndIsDeletedFalse(Long projectId, FeatureType type) {
        return jpaRepository.countByProjectIdAndTypeAndIsDeletedFalse(projectId, type);
    }
}
