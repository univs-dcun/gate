package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLogJpaRepository extends JpaRepository<LoginLog, Long> {
}
