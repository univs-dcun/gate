package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.palm_feature.application.input.PalmFeatureQuery;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import ai.univs.gate.modules.palm_feature.domain.repository.PalmFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PalmFeatureRepositoryImpl implements PalmFeatureRepository {

    private final PalmFeatureJpaRepository jpaRepository;
    private final PalmFeatureDSLRepository dslRepository;

    @Override
    public PalmFeature save(PalmFeature palmFeature) {
        return jpaRepository.save(palmFeature);
    }

    @Override
    public Optional<PalmFeature> findByIdAndIsDeletedFalse(Long id) {
        return jpaRepository.findByIdAndIsDeletedFalse(id);
    }

    @Override
    public Optional<PalmFeature> findByFeatureIdAndProjectIdAndIsDeletedFalse(String featureId, Long projectId) {
        return jpaRepository.findByFeatureIdAndProjectIdAndIsDeletedFalse(featureId, projectId);
    }

    @Override
    public Page<PalmFeature> findAllByQuery(PalmFeatureQuery query, Long projectId) {
        return dslRepository.findAllByQuery(query, projectId);
    }

    @Override
    public long countByProjectIdAndIsDeletedFalse(Long projectId) {
        return jpaRepository.countByProjectIdAndIsDeletedFalse(projectId);
    }
}
