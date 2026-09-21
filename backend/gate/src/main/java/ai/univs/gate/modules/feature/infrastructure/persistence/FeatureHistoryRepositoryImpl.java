package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.repository.FeatureHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeatureHistoryRepositoryImpl implements FeatureHistoryRepository {

    private final FeatureHistoryJpaRepository featureHistoryJpaRepository;

    @Override
    public FeatureHistory save(FeatureHistory featureHistory) {
        return featureHistoryJpaRepository.save(featureHistory);
    }
}
