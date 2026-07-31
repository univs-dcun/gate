package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByJti(String jti) {
        return refreshTokenJpaRepository.findByJti(jti);
    }

    @Override
    public List<RefreshToken> findAllByAccountIdAndIsRevokedFalse(Long accountId) {
        return refreshTokenJpaRepository.findAllByAccountIdAndIsRevokedFalse(accountId);
    }

    @Override
    public boolean revokeIfActive(String jti, LocalDateTime revokedAt) {
        return refreshTokenJpaRepository.revokeByJtiIfActive(jti, revokedAt) > 0;
    }
}
