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

    // 폐기 직후 유예 창: 멀티탭 등 정상 클라이언트의 동시 갱신 경쟁을 탈취 재사용으로 오탐하지 않기 위한 시간
    private static final long REUSE_DETECTION_GRACE_SECONDS = 30;

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;

    // 재사용 탐지의 전체 폐기가 예외 롤백으로 무효화되지 않도록 noRollbackFor 지정
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResult execute(String refreshToken) {
        jwtTokenProvider.validateRefreshToken(refreshToken);

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (storedToken.getIsRevoked()) {
            handleRevokedTokenReuse(accountId, jti, storedToken.getRevokedAt());
        }

        if (storedToken.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        // 회전: 조건부 UPDATE로 동시 요청 중 하나만 성공 — 같은 토큰으로 새 토큰 2개가 발급되는 것 방지
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!refreshTokenRepository.revokeIfActive(jti, now)) {
            // 조회 후 이 시점 사이에 다른 요청이 먼저 회전함 — 유예 창 내 경쟁이므로 전체 폐기 없이 거절
            throw new InvalidRefreshTokenException();
        }

        RefreshTokenResult newRefreshToken = jwtTokenProvider.createRefreshToken(accountId);
        refreshTokenRepository.save(RefreshToken.builder()
                .accountId(accountId)
                .jti(newRefreshToken.jti())
                .tokenHash(TokenHasher.sha256Hex(newRefreshToken.token()))
                .issuedAt(now)
                .expiresAt(newRefreshToken.expiresAt())
                .isRevoked(false)
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build());

        String newAccessToken = jwtTokenProvider.createAccessToken(account);

        log.info("Access token republished with rotation: accountId={}", accountId);
        return TokenResult.of(newAccessToken, newRefreshToken.token());
    }

    private void handleRevokedTokenReuse(Long accountId, String jti, LocalDateTime revokedAt) {
        boolean withinGrace = revokedAt != null
                && revokedAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(REUSE_DETECTION_GRACE_SECONDS));

        // 유예 창을 지난 폐기 토큰의 재제시 = 탈취 의심 → 계정의 활성 토큰 전체 폐기
        if (!withinGrace) {
            List<RefreshToken> activeTokens = refreshTokenRepository.findAllByAccountIdAndIsRevokedFalse(accountId);
            activeTokens.forEach(RefreshToken::revokeToken);
            log.warn("Refresh token reuse detected — all sessions revoked: accountId={}, jti={}, revokedSessions={}, ip={}",
                    accountId, jti, activeTokens.size(), ClientRequestContextHolder.getIpAddress());
        }
        throw new InvalidRefreshTokenException();
    }
}
