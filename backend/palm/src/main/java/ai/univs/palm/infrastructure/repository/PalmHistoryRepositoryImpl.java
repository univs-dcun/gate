package ai.univs.palm.infrastructure.repository;

import ai.univs.palm.domain.PalmHistory;
import ai.univs.palm.domain.repository.PalmHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PalmHistoryRepositoryImpl implements PalmHistoryRepository {

    private final PalmHistoryJpaRepository repository;

    @Override
    public PalmHistory save(PalmHistory faceHistory) {
        return repository.save(faceHistory);
    }
}
