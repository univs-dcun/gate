package ai.univs.gate.modules.palm_media.infrastructure.persistence;

import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PalmMediaJpaRepository extends JpaRepository<PalmMedia, Long> {

    Optional<PalmMedia> findByIdAndIsDeletedFalse(Long id);

    Optional<PalmMedia> findByPalmIdAndProjectIdAndIsDeletedFalse(String palmId, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}
