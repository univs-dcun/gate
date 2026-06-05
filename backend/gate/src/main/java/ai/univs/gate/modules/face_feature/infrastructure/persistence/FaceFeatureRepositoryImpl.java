package ai.univs.gate.modules.face_feature.infrastructure.persistence;

import ai.univs.gate.modules.face_feature.application.input.FaceFeatureQuery;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.repository.FaceFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FaceFeatureRepositoryImpl implements FaceFeatureRepository {

    private final FaceFeatureJpaRepository faceFeatureJpaRepository;
    private final FaceFeatureDSLRepository faceFeatureDSLRepository;

    @Override
    public FaceFeature save(FaceFeature faceFeature) {
        return faceFeatureJpaRepository.save(faceFeature);
    }

    @Override
    public Optional<FaceFeature> findByIdAndIsDeletedFalse(Long faceFeatureId) {
        return faceFeatureJpaRepository.findByIdAndIsDeleted(faceFeatureId, false);
    }

    @Override
    public Optional<FaceFeature> findByFaceIdAndProjectIdAndIsDeletedFalse(String faceId, Long projectId) {
        return faceFeatureJpaRepository.findByFaceIdAndProjectIdAndIsDeleted(faceId, projectId, false);
    }

    @Override
    public Page<FaceFeature> findAllByQuery(FaceFeatureQuery query, Long projectId) {
        return faceFeatureDSLRepository.findAllByQuery(query, projectId);
    }

    @Override
    public long countByProjectIdAndIsDeletedFalse(Long projectId) {
        return faceFeatureJpaRepository.countByProjectIdAndIsDeleted(projectId, false);
    }
}
