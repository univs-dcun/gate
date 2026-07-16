package ai.univs.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.auth.application.exception.AccountInactiveException;
import ai.univs.auth.application.exception.AccountLockedException;
import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.BadCredentialsException;
import ai.univs.auth.application.result.LoginResult;
import ai.univs.auth.application.result.RefreshTokenResult;
import ai.univs.auth.support.security.TokenHasher;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.LoginLog;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.enums.LoginStatus;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.LoginLogRepository;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import ai.univs.auth.shared.web.ctx.ClientRequestContextHolder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUseCase 단위 테스트")
class LoginUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String EMAIL = "user@univs.ai";
    private static final String RAW_PASSWORD = "Password1!";
    private static final String ENCODED_PASSWORD = "{bcrypt}encoded-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String JTI = "550e8400-e29b-41d4-a716-446655440000";
    private static final String IP_ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "JUnit-Agent/1.0";

    @Mock private AccountRepository accountRepository;
    @Mock private LoginLogRepository loginLogRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private LoginUseCase loginUseCase;

    private Account account;

    @BeforeEach
    void setUp() {
        ClientRequestContextHolder.set(IP_ADDRESS, USER_AGENT);
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(3)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(30))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(30))
                .build();
    }

    @AfterEach
    void tearDown() {
        ClientRequestContextHolder.clear();
    }

    @Test
    @DisplayName("로그인 성공 시 토큰이 발급되고 리프레시 토큰과 성공 이력이 저장된다")
    void execute_success() {
        // given
        LocalDateTime refreshExpiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(14);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtTokenProvider.createAccessToken(account)).willReturn(ACCESS_TOKEN);
        given(jwtTokenProvider.createRefreshToken(ACCOUNT_ID))
                .willReturn(new RefreshTokenResult(REFRESH_TOKEN, JTI, refreshExpiresAt));

        // when
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        LoginResult result = loginUseCase.execute(EMAIL, RAW_PASSWORD);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        // then: 계정 상태 전이 검증 (실패 횟수 초기화 + 마지막 로그인 갱신)
        assertThat(account.getFailedLoginAttempts()).isZero();
        assertThat(account.getLockedUntil()).isNull();
        assertThat(account.getLastLoginAt()).isBetween(before, after);
        assertThat(account.getLastLoginIp()).isEqualTo(IP_ADDRESS);

        // then: 저장된 리프레시 토큰 검증
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedToken.getJti()).isEqualTo(JTI);
        // 원문 토큰이 아니라 SHA-256 해시가 저장되어야 한다 (DB 유출 시 세션 탈취 방지)
        assertThat(savedToken.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(REFRESH_TOKEN));
        assertThat(savedToken.getTokenHash()).isNotEqualTo(REFRESH_TOKEN);
        assertThat(savedToken.getIssuedAt()).isBetween(before, after);
        assertThat(savedToken.getExpiresAt()).isEqualTo(refreshExpiresAt);
        assertThat(savedToken.getIsRevoked()).isFalse();
        assertThat(savedToken.getIpAddress()).isEqualTo(IP_ADDRESS);
        assertThat(savedToken.getUserAgent()).isEqualTo(USER_AGENT);

        // then: 성공 로그인 이력 검증
        LoginLog savedLog = captureSingleLoginLog();
        assertThat(savedLog.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedLog.getAttemptedEmail()).isEqualTo(EMAIL);
        assertThat(savedLog.getLoginStatus()).isEqualTo(LoginStatus.SUCCESS);
        assertThat(savedLog.getLoginAt()).isBetween(before, after);
        assertThat(savedLog.getIpAddress()).isEqualTo(IP_ADDRESS);
        assertThat(savedLog.getUserAgent()).isEqualTo(USER_AGENT);

        // then: 결과 필드 exact 검증
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.accountResult().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.accountResult().email()).isEqualTo(EMAIL);
        assertThat(result.accountResult().lastLoginAt()).isEqualTo(account.getLastLoginAt());
    }

    @Test
    @DisplayName("존재하지 않는 계정이면 AccountNotFoundException이 발생하고 accountId 없는 실패 이력이 저장된다")
    void execute_accountNotFound_throwsException() {
        // given
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginUseCase.execute(EMAIL, RAW_PASSWORD))
                .isInstanceOf(AccountNotFoundException.class);

        // then: 실패 이력 검증 (계정을 못 찾았으므로 accountId는 null)
        LoginLog savedLog = captureSingleLoginLog();
        assertThat(savedLog.getAccountId()).isNull();
        assertThat(savedLog.getAttemptedEmail()).isEqualTo(EMAIL);
        assertThat(savedLog.getLoginStatus()).isEqualTo(LoginStatus.FAILED_ACCOUNT_NOT_FOUND);

        // then: 토큰 발급/저장이 일어나지 않아야 한다
        verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenRepository);
    }

    @Test
    @DisplayName("잠금 기간이 남은 계정이면 AccountLockedException이 발생하고 잠금 실패 이력이 저장된다")
    void execute_accountLocked_throwsException() {
        // given
        account.setStatus(AccountStatus.LOCKED);
        account.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> loginUseCase.execute(EMAIL, RAW_PASSWORD))
                .isInstanceOf(AccountLockedException.class);

        // then: 잠금 상태는 유지된다
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);

        LoginLog savedLog = captureSingleLoginLog();
        assertThat(savedLog.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedLog.getLoginStatus()).isEqualTo(LoginStatus.FAILED_ACCOUNT_LOCKED);

        verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenRepository);
    }

    @Test
    @DisplayName("잠금 기간이 지난 계정은 자동 해제되어 로그인에 성공한다")
    void execute_lockExpired_autoUnlocksAndSucceeds() {
        // given
        account.setStatus(AccountStatus.LOCKED);
        account.setFailedLoginAttempts(5);
        account.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtTokenProvider.createAccessToken(account)).willReturn(ACCESS_TOKEN);
        given(jwtTokenProvider.createRefreshToken(ACCOUNT_ID))
                .willReturn(new RefreshTokenResult(
                        REFRESH_TOKEN, JTI, LocalDateTime.now(ZoneOffset.UTC).plusDays(14)));

        // when
        LoginResult result = loginUseCase.execute(EMAIL, RAW_PASSWORD);

        // then: 잠금이 자동 해제되어야 한다
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getFailedLoginAttempts()).isZero();
        assertThat(account.getLockedUntil()).isNull();
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);

        LoginLog savedLog = captureSingleLoginLog();
        assertThat(savedLog.getLoginStatus()).isEqualTo(LoginStatus.SUCCESS);
    }

    @Test
    @DisplayName("비활성(INACTIVE) 계정이면 AccountInactiveException이 발생한다")
    void execute_accountInactive_throwsException() {
        // given
        account.setStatus(AccountStatus.INACTIVE);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> loginUseCase.execute(EMAIL, RAW_PASSWORD))
                .isInstanceOf(AccountInactiveException.class);

        // then: 비활성 계정도 FAILED_ACCOUNT_LOCKED 상태로 이력이 남는다
        LoginLog savedLog = captureSingleLoginLog();
        assertThat(savedLog.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedLog.getLoginStatus()).isEqualTo(LoginStatus.FAILED_ACCOUNT_LOCKED);

        verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenRepository);
    }

    @Test
    @DisplayName("비밀번호 불일치 시 BadCredentialsException이 발생하고 실패 횟수가 1 증가한다")
    void execute_wrongPassword_incrementsFailedAttempts() {
        // given
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> loginUseCase.execute(EMAIL, RAW_PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        // then: 실패 횟수 3 -> 4, 아직 잠기지는 않는다
        assertThat(account.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getLockedUntil()).isNull();

        LoginLog savedLog = captureSingleLoginLog();
        assertThat(savedLog.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedLog.getLoginStatus()).isEqualTo(LoginStatus.FAILED_WRONG_PASSWORD);

        verifyNoInteractions(jwtTokenProvider, refreshTokenRepository);
    }

    @Test
    @DisplayName("비밀번호 5회 실패 시 계정이 30분 잠금 상태로 전환된다")
    void execute_fifthWrongPassword_locksAccount() {
        // given: 이미 4회 실패한 계정
        account.setFailedLoginAttempts(4);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

        // when
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        assertThatThrownBy(() -> loginUseCase.execute(EMAIL, RAW_PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        // then: 5회째 실패로 잠금 전환 (30분)
        assertThat(account.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(account.getLockedUntil())
                .isBetween(before.plusMinutes(30), after.plusMinutes(30));

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    private LoginLog captureSingleLoginLog() {
        ArgumentCaptor<LoginLog> logCaptor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogRepository).save(logCaptor.capture());
        return logCaptor.getValue();
    }
}
