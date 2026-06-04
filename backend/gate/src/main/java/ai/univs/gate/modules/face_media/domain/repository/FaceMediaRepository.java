package ai.univs.gate.modules.face_media.domain.repository;

import ai.univs.gate.modules.face_media.application.input.FaceMediaQuery;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface FaceMediaRepository {

    FaceMedia save(FaceMedia faceMedia);

    Optional<FaceMedia> findByIdAndIsDeletedFalse(Long faceMediaId);

    Optional<FaceMedia> findByFaceIdAndProjectIdAndIsDeletedFalse(String faceId, Long projectId);

    Page<FaceMedia> findAllByQuery(FaceMediaQuery query, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}
