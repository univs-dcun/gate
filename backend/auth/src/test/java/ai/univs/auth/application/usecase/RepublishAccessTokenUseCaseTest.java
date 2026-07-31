package ai.univs.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.InvalidRefreshTokenException;
import ai.univs.auth.application.exception.InvalidRefreshTokenTypeException;
import ai.univs.auth.application.result.RefreshTokenResult;
import ai.univs.auth.application.result.TokenResult;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import ai.univs.auth.support.security.TokenHasher;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepublishAccessTokenUseCase 단위 테스트")
class RepublishAccessTokenUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String REFRESH_TOKEN_VALUE = "refresh-token";
    private static final String JTI = "550e8400-e29b-41d4-a716-446655440000";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String NEW_JTI = "660e8400-e29b-41d4-a716-446655440001";

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AccountRepository accountRepository;

    @InjectMocks private RepublishAccessTokenUseCase republishAccessTokenUseCase;

    private Account account;
    private RefreshToken storedToken;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .email("user@univs.ai")
                .password("{bcrypt}encoded-password")
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        storedToken = RefreshToken.builder()
                .tokenId(1L)
                .accountId(ACCOUNT_ID)
                .jti(JTI)
                .tokenHash(TokenHasher.sha256Hex(REFRESH_TOKEN_VALUE))
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(13))
                .isRevoked(false)
                .build();
    }

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새 액세스 토큰이 발급되고 리프레시 토큰이 회전된다")
    void execute_success_rotatesRefreshToken() {
        // given
        LocalDateTime newExpiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(14);
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(refreshTokenRepository.revokeIfActive(eq(JTI), any(LocalDateTime.class))).willReturn(true);
        given(jwtTokenProvider.createAccessToken(account)).willReturn(NEW_ACCESS_TOKEN);
        given(jwtTokenProvider.createRefreshToken(ACCOUNT_ID))
                .willReturn(new RefreshTokenResult(NEW_REFRESH_TOKEN, NEW_JTI, newExpiresAt));

        // when
        TokenResult result = republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE);

        // then: 서명/타입 검증이 반드시 호출되어야 한다
        verify(jwtTokenProvider).validateRefreshToken(REFRESH_TOKEN_VALUE);

        // then: 사용한 토큰은 조건부 UPDATE로 폐기(회전)되어야 한다
        verify(refreshTokenRepository).revokeIfActive(eq(JTI), any(LocalDateTime.class));

        // then: 새 리프레시 토큰이 해시로 저장되어야 한다
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedToken.getJti()).isEqualTo(NEW_JTI);
        assertThat(savedToken.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(NEW_REFRESH_TOKEN));
        assertThat(savedToken.getExpiresAt()).isEqualTo(newExpiresAt);
        assertThat(savedToken.getIsRevoked()).isFalse();

        // then: 결과 필드 exact 검증
        assertThat(result.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);
    }

    @Test
    @DisplayName("토큰 서명 검증에 실패하면 InvalidRefreshTokenException이 발생하고 저장소 조회가 일어나지 않는다")
    void execute_invalidSignature_throwsException() {
        // given
        willThrow(new InvalidRefreshTokenException())
                .given(jwtTokenProvider).validateRefreshToken(REFRESH_TOKEN_VALUE);

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(refreshTokenRepository, accountRepository);
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }

    @Test
    @DisplayName("리프레시 타입이 아닌 토큰이면 InvalidRefreshTokenTypeException이 그대로 전파된다")
    void execute_wrongTokenType_throwsException() {
        // given: 액세스 토큰을 리프레시 용도로 제출한 경우
        willThrow(new InvalidRefreshTokenTypeException())
                .given(jwtTokenProvider).validateRefreshToken(REFRESH_TOKEN_VALUE);

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenTypeException.class);

        verifyNoInteractions(refreshTokenRepository, accountRepository);
    }

    @Test
    @DisplayName("jti에 해당하는 저장 토큰이 없으면 InvalidRefreshTokenException이 발생한다")
    void execute_storedTokenNotFound_throwsException() {
        // given
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(accountRepository);
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }

    @Test
    @DisplayName("유예 창을 지난 폐기 토큰 재제시(재사용 탐지) 시 계정의 활성 토큰이 전부 폐기된다")
    void execute_revokedTokenReuse_revokesAllActiveTokens() {
        // given: 유예 창(30초)을 훨씬 지난 시점에 폐기된 토큰 + 계정에 남아있는 활성 토큰 2개
        storedToken.setIsRevoked(true);
        storedToken.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        RefreshToken activeToken1 = RefreshToken.builder()
                .tokenId(2L).accountId(ACCOUNT_ID).jti("jti-2")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(14))
                .isRevoked(false).build();
        RefreshToken activeToken2 = RefreshToken.builder()
                .tokenId(3L).accountId(ACCOUNT_ID).jti("jti-3")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(14))
                .isRevoked(false).build();
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));
        given(refreshTokenRepository.findAllByAccountIdAndIsRevokedFalse(ACCOUNT_ID))
                .willReturn(List.of(activeToken1, activeToken2));

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // then: 계정의 모든 활성 토큰이 폐기되어야 한다 (세션 강제 종료)
        assertThat(activeToken1.getIsRevoked()).isTrue();
        assertThat(activeToken2.getIsRevoked()).isTrue();

        verifyNoInteractions(accountRepository);
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("유예 창(30초) 이내에 폐기된 토큰 재제시는 경쟁으로 보고 전체 폐기 없이 거절한다")
    void execute_revokedWithinGrace_rejectsWithoutRevokeAll() {
        // given: 방금(유예 창 이내) 다른 요청의 회전으로 폐기된 토큰 — 멀티탭 동시 갱신 시나리오
        storedToken.setIsRevoked(true);
        storedToken.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5));
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // then: 오탐 완화 — 계정 전체 토큰 폐기가 일어나지 않아야 한다
        verify(refreshTokenRepository, never()).findAllByAccountIdAndIsRevokedFalse(anyLong());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("조회 후 다른 요청이 먼저 회전하면(조건부 UPDATE 실패) 전체 폐기 없이 거절한다")
    void execute_concurrentRotation_rejectsWithoutRevokeAll() {
        // given: findByJti 시점에는 활성이었으나 revokeIfActive 시점에 이미 다른 요청이 회전함
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(refreshTokenRepository.revokeIfActive(eq(JTI), any(LocalDateTime.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // then: 같은 토큰으로 새 토큰이 2개 발급되지 않아야 한다
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).findAllByAccountIdAndIsRevokedFalse(anyLong());
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }

    @Test
    @DisplayName("저장 토큰이 만료됐으면 InvalidRefreshTokenException이 발생하고 회전이 일어나지 않는다")
    void execute_expiredStoredToken_throwsException() {
        // given
        storedToken.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // then: 만료 토큰은 재사용 탐지 대상이 아니다 — 전체 폐기가 일어나지 않는다
        verify(refreshTokenRepository, never()).findAllByAccountIdAndIsRevokedFalse(anyLong());
        verifyNoInteractions(accountRepository);
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }

    @Test
    @DisplayName("토큰의 계정이 존재하지 않으면 AccountNotFoundException이 발생하고 회전이 일어나지 않는다")
    void execute_accountNotFound_throwsException() {
        // given
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(AccountNotFoundException.class);

        // then: 계정 확인 전에는 기존 토큰을 폐기하지 않는다
        verify(refreshTokenRepository, never()).revokeIfActive(anyString(), any(LocalDateTime.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }
}
