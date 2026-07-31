package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.RefreshToken;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    List<RefreshToken> findAllByAccountIdAndIsRevokedFalse(Long accountId);

    @Modifying
    @Query("update RefreshToken t set t.isRevoked = true, t.revokedAt = :now "
            + "where t.jti = :jti and t.isRevoked = false")
    int revokeByJtiIfActive(@Param("jti") String jti, @Param("now") LocalDateTime now);
}
