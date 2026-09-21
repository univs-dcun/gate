package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchHistoryJpaRepository extends JpaRepository<MatchHistory, Long> {
}
