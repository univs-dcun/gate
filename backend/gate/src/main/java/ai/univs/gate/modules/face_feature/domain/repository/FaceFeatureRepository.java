package ai.univs.gate.modules.face_feature.domain.repository;

import ai.univs.gate.modules.face_feature.application.input.FaceFeatureQuery;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface FaceFeatureRepository {

    FaceFeature save(FaceFeature faceFeature);

    Optional<FaceFeature> findByIdAndIsDeletedFalse(Long faceFeatureId);

    Optional<FaceFeature> findByFeatureIdAndProjectIdAndIsDeletedFalse(String featureId, Long projectId);

    Page<FaceFeature> findAllByQuery(FaceFeatureQuery query, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}
