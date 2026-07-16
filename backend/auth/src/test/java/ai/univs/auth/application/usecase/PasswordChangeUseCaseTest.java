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
import ai.univs.auth.application.exception.InvalidPasswordException;
import ai.univs.auth.application.exception.PasswordReusedException;
import ai.univs.auth.application.input.PasswordChangeInput;
import ai.univs.auth.application.result.PasswordChangeResult;
import ai.univs.auth.application.service.PasswordService;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.enums.PasswordResetMethod;
import ai.univs.auth.domain.repository.AccountRepository;
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
@DisplayName("PasswordChangeUseCase 단위 테스트")
class PasswordChangeUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String CURRENT_RAW_PASSWORD = "Current1!";
    private static final String CURRENT_ENCODED_PASSWORD = "{bcrypt}current-encoded";
    private static final String NEW_RAW_PASSWORD = "NewPassword1!";
    private static final String NEW_ENCODED_PASSWORD = "{bcrypt}new-encoded";
    private static final String IP_ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "JUnit-Agent/1.0";

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private PasswordService passwordService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private PasswordChangeUseCase passwordChangeUseCase;

    private Account account;
    private PasswordChangeInput input;

    @BeforeEach
    void setUp() {
        ClientRequestContextHolder.set(IP_ADDRESS, USER_AGENT);
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .email("user@univs.ai")
                .password(CURRENT_ENCODED_PASSWORD)
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(10))
                .build();
        input = new PasswordChangeInput(ACCOUNT_ID, CURRENT_RAW_PASSWORD, NEW_RAW_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        ClientRequestContextHolder.clear();
    }

    @Test
    @DisplayName("변경 성공 시 새 비밀번호가 인코딩되어 반영되고 변경 이력이 저장된다")
    void execute_success() {
        // given
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(CURRENT_RAW_PASSWORD, CURRENT_ENCODED_PASSWORD)).willReturn(true);
        given(passwordService.isPasswordReused(ACCOUNT_ID, NEW_RAW_PASSWORD)).willReturn(false);
        given(passwordEncoder.encode(NEW_RAW_PASSWORD)).willReturn(NEW_ENCODED_PASSWORD);

        // when
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        PasswordChangeResult result = passwordChangeUseCase.execute(input);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        // then: 계정에 인코딩된 새 비밀번호가 반영되어야 한다 (평문 저장 금지)
        assertThat(account.getPassword()).isEqualTo(NEW_ENCODED_PASSWORD);
        assertThat(account.getUpdatedAt()).isBetween(before, after);

        // then: 저장된 변경 이력 필드 검증
        ArgumentCaptor<PasswordHistory> historyCaptor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        PasswordHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedHistory.getPasswordHash()).isEqualTo(NEW_ENCODED_PASSWORD);
        assertThat(savedHistory.getPasswordResetMethod()).isEqualTo(PasswordResetMethod.USER_CHANGE);
        assertThat(savedHistory.getChangedAt()).isBetween(before, after);
        assertThat(savedHistory.getIpAddress()).isEqualTo(IP_ADDRESS);
        assertThat(savedHistory.getUserAgent()).isEqualTo(USER_AGENT);

        // then: 결과 필드 검증
        assertThat(result.AccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    @DisplayName("계정이 존재하지 않으면 AccountNotFoundException이 발생한다")
    void execute_accountNotFound_throwsException() {
        // given
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> passwordChangeUseCase.execute(input))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(passwordEncoder, passwordService, passwordHistoryRepository);
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 InvalidPasswordException이 발생하고 비밀번호는 유지된다")
    void execute_wrongCurrentPassword_throwsException() {
        // given
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(CURRENT_RAW_PASSWORD, CURRENT_ENCODED_PASSWORD)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> passwordChangeUseCase.execute(input))
                .isInstanceOf(InvalidPasswordException.class);

        // then: 비밀번호 변경/이력 저장이 일어나지 않아야 한다
        assertThat(account.getPassword()).isEqualTo(CURRENT_ENCODED_PASSWORD);
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(passwordService, passwordHistoryRepository);
    }

    @Test
    @DisplayName("직전에 사용한 비밀번호로 변경 시도하면 PasswordReusedException이 발생한다")
    void execute_reusedPassword_throwsException() {
        // given
        given(accountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(CURRENT_RAW_PASSWORD, CURRENT_ENCODED_PASSWORD)).willReturn(true);
        given(passwordService.isPasswordReused(ACCOUNT_ID, NEW_RAW_PASSWORD)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> passwordChangeUseCase.execute(input))
                .isInstanceOf(PasswordReusedException.class);

        // then: 비밀번호 변경/이력 저장이 일어나지 않아야 한다
        assertThat(account.getPassword()).isEqualTo(CURRENT_ENCODED_PASSWORD);
        verify(passwordEncoder, never()).encode(anyString());
        verify(passwordHistoryRepository, never()).save(any(PasswordHistory.class));
    }
}
