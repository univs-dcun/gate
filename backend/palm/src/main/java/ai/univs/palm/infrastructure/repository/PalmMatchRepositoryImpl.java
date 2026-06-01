package ai.univs.palm.infrastructure.repository;

import ai.univs.palm.domain.PalmMatch;
import ai.univs.palm.domain.repository.PalmMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PalmMatchRepositoryImpl implements PalmMatchRepository {

    private final PalmMatchJpaRepository faceMatchJpaRepository;

    @Override
    public PalmMatch save(PalmMatch faceMatch) {
        return faceMatchJpaRepository.save(faceMatch);
    }
}
