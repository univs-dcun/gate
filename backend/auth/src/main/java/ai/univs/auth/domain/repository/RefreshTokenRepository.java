package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByJti(String jti);

    List<RefreshToken> findAllByAccountIdAndIsRevokedFalse(Long accountId);
}
