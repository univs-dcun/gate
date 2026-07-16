package ai.univs.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.EmailNotVerifiedException;
import ai.univs.auth.application.exception.PasswordMismatchException;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.enums.EmailVerificationType;
import ai.univs.auth.domain.enums.PasswordResetMethod;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
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
@DisplayName("PasswordResetUseCase 단위 테스트")
class PasswordResetUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String EMAIL = "user@univs.ai";
    private static final String OLD_ENCODED_PASSWORD = "{bcrypt}old-encoded";
    private static final String NEW_RAW_PASSWORD = "NewPassword1!";
    private static final String NEW_ENCODED_PASSWORD = "{bcrypt}new-encoded";
    private static final String IP_ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "JUnit-Agent/1.0";

    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;

    @InjectMocks private PasswordResetUseCase passwordResetUseCase;

    private Account account;
    private EmailVerification verifiedVerification;

    @BeforeEach
    void setUp() {
        ClientRequestContextHolder.set(IP_ADDRESS, USER_AGENT);
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .email(EMAIL)
                .password(OLD_ENCODED_PASSWORD)
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(10))
                .build();
        verifiedVerification = EmailVerification.builder()
                .verificationId(1L)
                .email(EMAIL)
                .type(EmailVerificationType.PASSWORD_RESET)
                .verificationCode("123456")
                .createdAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))
                .verified(true)
                .verifiedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .attempts(1)
                .build();
    }

    @AfterEach
    void tearDown() {
        ClientRequestContextHolder.clear();
    }

    @Test
    @DisplayName("재설정 성공 시 기존 해시가 이력으로 저장되고 새 비밀번호가 인코딩되어 반영된다")
    void execute_success() {
        // given
        given(emailVerificationRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(EMAIL, EmailVerificationType.PASSWORD_RESET))
                .willReturn(Optional.of(verifiedVerification));
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(passwordEncoder.encode(NEW_RAW_PASSWORD)).willReturn(NEW_ENCODED_PASSWORD);

        // when
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        passwordResetUseCase.execute(EMAIL, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        // then: 이력에는 변경 전(기존) 해시가 저장되어야 한다
        ArgumentCaptor<PasswordHistory> historyCaptor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        PasswordHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedHistory.getPasswordHash()).isEqualTo(OLD_ENCODED_PASSWORD);
        assertThat(savedHistory.getPasswordResetMethod()).isEqualTo(PasswordResetMethod.EMAIL_RESET);
        assertThat(savedHistory.getChangedAt()).isBetween(before, after);
        assertThat(savedHistory.getIpAddress()).isEqualTo(IP_ADDRESS);
        assertThat(savedHistory.getUserAgent()).isEqualTo(USER_AGENT);

        // then: 계정에 인코딩된 새 비밀번호가 반영되어야 한다 (평문 저장 금지)
        assertThat(account.getPassword()).isEqualTo(NEW_ENCODED_PASSWORD);
        assertThat(account.getUpdatedAt()).isBetween(before, after);

        // then: 사용한 인증 레코드가 소진되어야 한다 (동일 인증으로 반복 재설정 방지)
        verify(emailVerificationRepository).deleteByEmailAndType(EMAIL, EmailVerificationType.PASSWORD_RESET);
    }

    @Test
    @DisplayName("인증 완료 후 유효 시간이 지난 인증은 사용할 수 없어 EmailNotVerifiedException이 발생한다")
    void execute_verificationStale_throwsException() {
        // given: 인증은 완료했으나 인증 시각이 유효 창(30분)을 벗어남
        verifiedVerification.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(31));
        given(emailVerificationRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(EMAIL, EmailVerificationType.PASSWORD_RESET))
                .willReturn(Optional.of(verifiedVerification));

        // when & then
        assertThatThrownBy(() -> passwordResetUseCase.execute(EMAIL, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(emailVerificationRepository, never()).deleteByEmailAndType(anyString(), any());
        verifyNoInteractions(accountRepository, passwordEncoder, passwordHistoryRepository);
    }

    @Test
    @DisplayName("이메일 인증 요청 이력이 없으면 EmailNotVerifiedException이 발생한다")
    void execute_verificationNotFound_throwsException() {
        // given
        given(emailVerificationRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(EMAIL, EmailVerificationType.PASSWORD_RESET))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> passwordResetUseCase.execute(EMAIL, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(accountRepository, passwordEncoder, passwordHistoryRepository);
    }

    @Test
    @DisplayName("이메일 인증이 완료되지 않았으면 EmailNotVerifiedException이 발생한다")
    void execute_notVerified_throwsException() {
        // given
        verifiedVerification.setVerified(false);
        given(emailVerificationRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(EMAIL, EmailVerificationType.PASSWORD_RESET))
                .willReturn(Optional.of(verifiedVerification));

        // when & then
        assertThatThrownBy(() -> passwordResetUseCase.execute(EMAIL, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(accountRepository, passwordEncoder, passwordHistoryRepository);
    }

    @Test
    @DisplayName("새 비밀번호와 확인 비밀번호가 다르면 PasswordMismatchException이 발생한다")
    void execute_passwordConfirmMismatch_throwsException() {
        // given
        given(emailVerificationRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(EMAIL, EmailVerificationType.PASSWORD_RESET))
                .willReturn(Optional.of(verifiedVerification));

        // when & then
        assertThatThrownBy(() -> passwordResetUseCase.execute(EMAIL, NEW_RAW_PASSWORD, "Different1!"))
                .isInstanceOf(PasswordMismatchException.class);

        // then: 계정 조회 전에 차단되어야 한다
        verifyNoInteractions(accountRepository, passwordEncoder, passwordHistoryRepository);
    }

    @Test
    @DisplayName("계정이 존재하지 않으면 AccountNotFoundException이 발생하고 이력이 저장되지 않는다")
    void execute_accountNotFound_throwsException() {
        // given
        given(emailVerificationRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(EMAIL, EmailVerificationType.PASSWORD_RESET))
                .willReturn(Optional.of(verifiedVerification));
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> passwordResetUseCase.execute(EMAIL, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD))
                .isInstanceOf(AccountNotFoundException.class);

        verify(passwordHistoryRepository, never()).save(any(PasswordHistory.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}
