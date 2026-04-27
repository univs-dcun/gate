package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.InvalidRefreshTokenException;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void execute(String refreshToken) {
        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);

        RefreshToken token = refreshTokenRepository.findByJti(jti)
                .orElseThrow(InvalidRefreshTokenException::new);

        // 토큰 생명주기 제거
        token.revokeToken();

        log.info("Logout: accountId={}", token.getAccountId());
    }
}
