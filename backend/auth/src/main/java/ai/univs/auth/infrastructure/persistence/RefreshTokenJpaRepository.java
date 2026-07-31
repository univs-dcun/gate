package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    List<RefreshToken> findAllByAccountIdAndIsRevokedFalse(Long accountId);
}
