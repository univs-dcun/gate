package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.InvalidRefreshTokenException;
import ai.univs.auth.application.result.RefreshTokenResult;
import ai.univs.auth.application.result.TokenResult;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import ai.univs.auth.shared.web.ctx.ClientRequestContextHolder;
import ai.univs.auth.support.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepublishAccessTokenUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public TokenResult execute(String refreshToken) {
        jwtTokenProvider.validateRefreshToken(refreshToken);

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(InvalidRefreshTokenException::new);

        // 이미 회전으로 폐기된 토큰 재제시 = 재사용(탈취 의심) → 계정의 활성 토큰 전체 폐기
        if (storedToken.getIsRevoked()) {
            revokeAllActiveTokens(accountId);
            log.warn("Refresh token reuse detected — all sessions revoked: accountId={}, jti={}, ip={}",
                    accountId, jti, ClientRequestContextHolder.getIpAddress());
            throw new InvalidRefreshTokenException();
        }

        if (storedToken.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        // 회전: 사용한 리프레시 토큰은 폐기하고 새 토큰으로 교체
        storedToken.revokeToken();

        RefreshTokenResult newRefreshToken = jwtTokenProvider.createRefreshToken(accountId);
        refreshTokenRepository.save(RefreshToken.builder()
                .accountId(accountId)
                .jti(newRefreshToken.jti())
                .tokenHash(TokenHasher.sha256Hex(newRefreshToken.token()))
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(newRefreshToken.expiresAt())
                .isRevoked(false)
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build());

        String newAccessToken = jwtTokenProvider.createAccessToken(account);

        log.info("Access token republished with rotation: accountId={}", accountId);
        return TokenResult.of(newAccessToken, newRefreshToken.token());
    }

    private void revokeAllActiveTokens(Long accountId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByAccountIdAndIsRevokedFalse(accountId);
        activeTokens.forEach(RefreshToken::revokeToken);
    }
}
