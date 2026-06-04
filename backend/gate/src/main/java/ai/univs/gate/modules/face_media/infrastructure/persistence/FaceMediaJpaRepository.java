package ai.univs.gate.modules.face_media.infrastructure.persistence;

import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaceMediaJpaRepository extends JpaRepository<FaceMedia, Long> {

    Optional<FaceMedia> findByIdAndIsDeleted(Long id, boolean isDeleted);

    Optional<FaceMedia> findByFaceIdAndProjectIdAndIsDeleted(String faceId, Long projectId, boolean isDeleted);

    long countByProjectIdAndIsDeleted(Long projectId, boolean isDeleted);
}
