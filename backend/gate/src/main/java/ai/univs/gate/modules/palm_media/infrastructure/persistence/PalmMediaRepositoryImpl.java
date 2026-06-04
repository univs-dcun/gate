package ai.univs.gate.modules.palm_media.infrastructure.persistence;

import ai.univs.gate.modules.palm_media.application.input.PalmMediaQuery;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.repository.PalmMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PalmMediaRepositoryImpl implements PalmMediaRepository {

    private final PalmMediaJpaRepository jpaRepository;
    private final PalmMediaDSLRepository dslRepository;

    @Override
    public PalmMedia save(PalmMedia palmMedia) {
        return jpaRepository.save(palmMedia);
    }

    @Override
    public Optional<PalmMedia> findByIdAndIsDeletedFalse(Long id) {
        return jpaRepository.findByIdAndIsDeletedFalse(id);
    }

    @Override
    public Optional<PalmMedia> findByPalmIdAndProjectIdAndIsDeletedFalse(String palmId, Long projectId) {
        return jpaRepository.findByPalmIdAndProjectIdAndIsDeletedFalse(palmId, projectId);
    }

    @Override
    public Page<PalmMedia> findAllByQuery(PalmMediaQuery query, Long projectId) {
        return dslRepository.findAllByQuery(query, projectId);
    }

    @Override
    public long countByProjectIdAndIsDeletedFalse(Long projectId) {
        return jpaRepository.countByProjectIdAndIsDeletedFalse(projectId);
    }
}
