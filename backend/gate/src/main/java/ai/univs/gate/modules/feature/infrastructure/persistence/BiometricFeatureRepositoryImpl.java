package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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
    public List<BiometricFeature> findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
            Collection<String> featureIds, Long projectId, FeatureType type) {
        // 빈 컬렉션으로 IN 절을 만들면 방언에 따라 문법 오류가 난다. 부를 일 자체가 없어야
        // 정상이지만, 후보가 0건인 경로가 실재하므로 여기서 막는다.
        if (featureIds.isEmpty()) return List.of();
        return jpaRepository.findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
                featureIds, projectId, type);
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
