package ai.univs.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.auth.application.event.AccountCreatedEvent;
import ai.univs.auth.application.exception.DuplicateEmailException;
import ai.univs.auth.application.exception.EmailNotVerifiedException;
import ai.univs.auth.application.exception.PasswordMismatchException;
import ai.univs.auth.application.input.SignupInput;
import ai.univs.auth.application.result.SignupResult;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.enums.EmailVerificationType;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignupUseCase 단위 테스트")
class SignupUseCaseTest {

    private static final Long ACCOUNT_ID = 11L;
    private static final String EMAIL = "newuser@univs.ai";
    private static final String RAW_PASSWORD = "Password1!";
    private static final String ENCODED_PASSWORD = "{bcrypt}encoded-password";

    @Mock private AccountRepository accountRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private SignupUseCase signupUseCase;

    private SignupInput input;
    private EmailVerification verifiedVerification;

    @BeforeEach
    void setUp() {
        input = new SignupInput(EMAIL, RAW_PASSWORD, RAW_PASSWORD);
        verifiedVerification = EmailVerification.builder()
                .verificationId(1L)
                .email(EMAIL)
                .type(EmailVerificationType.SIGNUP)
                .verificationCode("123456")
                .createdAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))
                .verified(true)
                .verifiedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .attempts(1)
                .build();
    }

    @Test
    @DisplayName("가입 성공 시 인코딩된 비밀번호로 활성 계정이 생성되고 계정 생성 이벤트가 발행된다")
    void execute_success() {
        // given
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(Optional.of(verifiedVerification));
        given(accountRepository.existsByEmail(EMAIL)).willReturn(false);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
        given(accountRepository.save(any(Account.class))).willAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setAccountId(ACCOUNT_ID);
            return saved;
        });

        // when
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        SignupResult result = signupUseCase.execute(input);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        // then: 저장된 계정 필드 검증 (평문 비밀번호가 저장되면 안 된다)
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo(EMAIL);
        assertThat(savedAccount.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedAccount.getFailedLoginAttempts()).isZero();
        assertThat(savedAccount.getCreatedAt()).isBetween(before, after);
        assertThat(savedAccount.getUpdatedAt()).isBetween(before, after);

        // then: 계정 생성 이벤트 필드 검증
        ArgumentCaptor<AccountCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(AccountCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);

        // then: 결과 필드 exact 검증
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.createdAt()).isEqualTo(savedAccount.getCreatedAt());
    }

    @Test
    @DisplayName("이메일 인증 요청 이력이 없으면 EmailNotVerifiedException이 발생한다")
    void execute_verificationNotFound_throwsException() {
        // given
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> signupUseCase.execute(input))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(accountRepository, passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("이메일 인증이 완료되지 않았으면 EmailNotVerifiedException이 발생한다")
    void execute_notVerified_throwsException() {
        // given
        verifiedVerification.setVerified(false);
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(Optional.of(verifiedVerification));

        // when & then
        assertThatThrownBy(() -> signupUseCase.execute(input))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(accountRepository, passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("비밀번호와 확인 비밀번호가 다르면 PasswordMismatchException이 발생한다")
    void execute_passwordMismatch_throwsException() {
        // given
        SignupInput mismatchedInput = new SignupInput(EMAIL, RAW_PASSWORD, "Different1!");
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(Optional.of(verifiedVerification));

        // when & then
        assertThatThrownBy(() -> signupUseCase.execute(mismatchedInput))
                .isInstanceOf(PasswordMismatchException.class);

        // then: 중복 이메일 확인 전에 차단되어야 한다
        verifyNoInteractions(accountRepository, passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("비밀번호가 null이면 PasswordMismatchException이 발생한다")
    void execute_nullPassword_throwsException() {
        // given
        SignupInput nullPasswordInput = new SignupInput(EMAIL, null, null);
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(Optional.of(verifiedVerification));

        // when & then
        assertThatThrownBy(() -> signupUseCase.execute(nullPasswordInput))
                .isInstanceOf(PasswordMismatchException.class);

        verifyNoInteractions(accountRepository, passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("이미 사용중인 이메일이면 DuplicateEmailException이 발생하고 계정이 생성되지 않는다")
    void execute_duplicateEmail_throwsException() {
        // given
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(Optional.of(verifiedVerification));
        given(accountRepository.existsByEmail(EMAIL)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> signupUseCase.execute(input))
                .isInstanceOf(DuplicateEmailException.class);

        verify(accountRepository, never()).save(any(Account.class));
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(eventPublisher);
    }
}
