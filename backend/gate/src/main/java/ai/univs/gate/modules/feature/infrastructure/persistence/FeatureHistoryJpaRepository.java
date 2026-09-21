package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureHistoryJpaRepository extends JpaRepository<FeatureHistory, Long> {
}
