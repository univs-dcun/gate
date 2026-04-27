package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByJti(String jti);
}
