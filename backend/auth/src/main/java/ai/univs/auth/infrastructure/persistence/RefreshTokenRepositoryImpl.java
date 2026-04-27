package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}
