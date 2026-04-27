package ai.univs.face.infrastructure.repository;

import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FaceHistoryRepositoryImpl implements FaceHistoryRepository {

    private final FaceHistoryJpaRepository repository;

    @Override
    public FaceHistory save(FaceHistory faceHistory) {
        return repository.save(faceHistory);
    }
}
