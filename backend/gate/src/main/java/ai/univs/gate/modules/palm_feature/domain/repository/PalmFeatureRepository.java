package ai.univs.gate.modules.palm_feature.domain.repository;

import ai.univs.gate.modules.palm_feature.application.input.PalmFeatureQuery;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface PalmFeatureRepository {

    PalmFeature save(PalmFeature palmFeature);

    Optional<PalmFeature> findByIdAndIsDeletedFalse(Long id);

    Optional<PalmFeature> findByFeatureIdAndProjectIdAndIsDeletedFalse(String featureId, Long projectId);

    Page<PalmFeature> findAllByQuery(PalmFeatureQuery query, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}
