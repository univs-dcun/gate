package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MatchHistoryRepositoryImpl implements MatchHistoryRepository {

    private final MatchHistoryJpaRepository matchHistoryJpaRepository;

    @Override
    public MatchHistory save(MatchHistory matchHistory) {
        return matchHistoryJpaRepository.save(matchHistory);
    }
}
