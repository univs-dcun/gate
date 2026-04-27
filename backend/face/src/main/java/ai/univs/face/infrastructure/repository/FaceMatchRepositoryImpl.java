package ai.univs.face.infrastructure.repository;

import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.repository.FaceMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FaceMatchRepositoryImpl implements FaceMatchRepository {

    private final FaceMatchJpaRepository faceMatchJpaRepository;

    @Override
    public FaceMatch save(FaceMatch faceMatch) {
        return faceMatchJpaRepository.save(faceMatch);
    }
}
