package ai.univs.gate.modules.palm_media.domain.repository;

import ai.univs.gate.modules.palm_media.application.input.PalmMediaQuery;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface PalmMediaRepository {

    PalmMedia save(PalmMedia palmMedia);

    Optional<PalmMedia> findByIdAndIsDeletedFalse(Long id);

    Optional<PalmMedia> findByPalmIdAndProjectIdAndIsDeletedFalse(String palmId, Long projectId);

    Page<PalmMedia> findAllByQuery(PalmMediaQuery query, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}
