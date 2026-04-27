package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountInactiveException;
import ai.univs.auth.application.exception.AccountLockedException;
import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.BadCredentialsException;
import ai.univs.auth.application.result.AccountResult;
import ai.univs.auth.application.result.LoginResult;
import ai.univs.auth.application.result.RefreshTokenResult;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.LoginLog;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.enums.LoginStatus;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.LoginLogRepository;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import ai.univs.auth.shared.web.ctx.ClientRequestContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUseCase {

    private final AccountRepository accountRepository;
    private final LoginLogRepository loginLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional(noRollbackFor = {
            AccountNotFoundException.class,
            AccountLockedException.class,
            AccountInactiveException.class,
            BadCredentialsException.class
    })
    public LoginResult execute(String email, String password) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logLoginAttempt(null, email, LoginStatus.FAILED_ACCOUNT_NOT_FOUND);
                    return new AccountNotFoundException();
                });

        // 잠김 계정 확인
        if (account.isLocked()) {
            logLoginAttempt(account.getAccountId(), email, LoginStatus.FAILED_ACCOUNT_LOCKED);
            throw new AccountLockedException();
        }

        // 계정 활성화 여부 확인
        if (!account.isActive()) {
            logLoginAttempt(account.getAccountId(), email, LoginStatus.FAILED_ACCOUNT_LOCKED);
            throw new AccountInactiveException();
        }

        // 비밀번호 확인
        if (!passwordEncoder.matches(password, account.getPassword())) {
            account.incrementFailedAttempts();
            logLoginAttempt(account.getAccountId(), email, LoginStatus.FAILED_WRONG_PASSWORD);
            throw new BadCredentialsException();
        }

        // 로그인 성공시 비밀번호 실패 횟수 초기화
        account.resetFailedAttempts();

        // 로그인 성공 이력 저장
        account.updateLastLogin(ClientRequestContextHolder.getIpAddress());

        // 엑세스 토큰, 리프레시 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(account);
        RefreshTokenResult refreshTokenResult = jwtTokenProvider.createRefreshToken(account.getAccountId());

        RefreshToken refreshToken = RefreshToken.builder()
                .accountId(account.getAccountId())
                .jti(refreshTokenResult.jti())
                .tokenHash(refreshTokenResult.token())
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(refreshTokenResult.expiresAt())
                .isRevoked(false)
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build();
        refreshTokenRepository.save(refreshToken);

        // 로그인 성공 이력 저장
        logLoginAttempt(account.getAccountId(), email, LoginStatus.SUCCESS);
        log.info("Login succeeded: accountId={}, email={}, ip={}",
                account.getAccountId(),
                email,
                ClientRequestContextHolder.getIpAddress());

        return LoginResult.of(
                accessToken,
                refreshTokenResult.token(),
                new AccountResult(
                        account.getAccountId(),
                        account.getEmail(),
                        account.getLastLoginAt()));
    }

    private void logLoginAttempt(Long accountId, String email, LoginStatus status) {
        loginLogRepository.save(LoginLog.builder()
                .accountId(accountId)
                .attemptedEmail(email)
                .loginStatus(status)
                .loginAt(LocalDateTime.now(ZoneOffset.UTC))
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build());
    }
}
