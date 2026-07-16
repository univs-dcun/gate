package ai.univs.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.InvalidRefreshTokenException;
import ai.univs.auth.application.exception.InvalidRefreshTokenTypeException;
import ai.univs.auth.application.result.TokenResult;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                .tokenHash(REFRESH_TOKEN_VALUE)
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(13))
                .isRevoked(false)
                .build();
    }

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새 액세스 토큰이 발급된다")
    void execute_success() {
        // given
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(jwtTokenProvider.createAccessToken(account)).willReturn(NEW_ACCESS_TOKEN);

        // when
        TokenResult result = republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE);

        // then: 서명/타입 검증이 반드시 호출되어야 한다
        verify(jwtTokenProvider).validateRefreshToken(REFRESH_TOKEN_VALUE);

        // then: 결과 필드 exact 검증
        assertThat(result.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
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
    @DisplayName("폐기(revoked)된 저장 토큰이면 InvalidRefreshTokenException이 발생한다")
    void execute_revokedToken_throwsException() {
        // given
        storedToken.setIsRevoked(true);
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(accountRepository);
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }

    @Test
    @DisplayName("저장 토큰이 만료됐으면 InvalidRefreshTokenException이 발생한다")
    void execute_expiredStoredToken_throwsException() {
        // given
        storedToken.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(accountRepository);
        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }

    @Test
    @DisplayName("토큰의 계정이 존재하지 않으면 AccountNotFoundException이 발생한다")
    void execute_accountNotFound_throwsException() {
        // given
        given(jwtTokenProvider.getJtiFromToken(REFRESH_TOKEN_VALUE)).willReturn(JTI);
        given(jwtTokenProvider.getAccountIdFromToken(REFRESH_TOKEN_VALUE)).willReturn(ACCOUNT_ID);
        given(refreshTokenRepository.findByJti(JTI)).willReturn(Optional.of(storedToken));
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> republishAccessTokenUseCase.execute(REFRESH_TOKEN_VALUE))
                .isInstanceOf(AccountNotFoundException.class);

        verify(jwtTokenProvider, never()).createAccessToken(any(Account.class));
    }
}
