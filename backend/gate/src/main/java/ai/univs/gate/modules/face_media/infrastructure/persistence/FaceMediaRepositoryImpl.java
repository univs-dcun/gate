package ai.univs.gate.modules.face_media.infrastructure.persistence;

import ai.univs.gate.modules.face_media.application.input.FaceMediaQuery;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.repository.FaceMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FaceMediaRepositoryImpl implements FaceMediaRepository {

    private final FaceMediaJpaRepository faceMediaJpaRepository;
    private final FaceMediaDSLRepository faceMediaDSLRepository;

    @Override
    public FaceMedia save(FaceMedia faceMedia) {
        return faceMediaJpaRepository.save(faceMedia);
    }

    @Override
    public Optional<FaceMedia> findByIdAndIsDeletedFalse(Long faceMediaId) {
        return faceMediaJpaRepository.findByIdAndIsDeleted(faceMediaId, false);
    }

    @Override
    public Optional<FaceMedia> findByFaceIdAndProjectIdAndIsDeletedFalse(String faceId, Long projectId) {
        return faceMediaJpaRepository.findByFaceIdAndProjectIdAndIsDeleted(faceId, projectId, false);
    }

    @Override
    public Page<FaceMedia> findAllByQuery(FaceMediaQuery query, Long projectId) {
        return faceMediaDSLRepository.findAllByQuery(query, projectId);
    }

    @Override
    public long countByProjectIdAndIsDeletedFalse(Long projectId) {
        return faceMediaJpaRepository.countByProjectIdAndIsDeleted(projectId, false);
    }
}
