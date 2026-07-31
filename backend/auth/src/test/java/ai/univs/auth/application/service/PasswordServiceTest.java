package ai.univs.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordService 단위 테스트")
class PasswordServiceTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String CURRENT_ENCODED_PASSWORD = "{bcrypt}current-encoded";
    private static final String NEW_RAW_PASSWORD = "NewPassword1!";

    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private PasswordService passwordService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .email("user@univs.ai")
                .password(CURRENT_ENCODED_PASSWORD)
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    @DisplayName("현재 비밀번호와 일치하면 이력 조회 없이 재사용으로 판정한다")
    void isPasswordReused_matchesCurrentPassword_returnsTrue() {
        // given
        given(passwordEncoder.matches(NEW_RAW_PASSWORD, CURRENT_ENCODED_PASSWORD)).willReturn(true);

        // when & then
        assertThat(passwordService.isPasswordReused(account, NEW_RAW_PASSWORD)).isTrue();
        verifyNoInteractions(passwordHistoryRepository);
    }

    @Test
    @DisplayName("최근 이력 5건 중 하나와 일치하면 재사용으로 판정한다")
    void isPasswordReused_matchesHistory_returnsTrue() {
        // given
        given(passwordEncoder.matches(NEW_RAW_PASSWORD, CURRENT_ENCODED_PASSWORD)).willReturn(false);
        given(passwordHistoryRepository.findRecentByAccountId(ACCOUNT_ID, 5))
                .willReturn(List.of(
                        PasswordHistory.builder().passwordHash("{bcrypt}hash-1").build(),
                        PasswordHistory.builder().passwordHash("{bcrypt}hash-2").build()));
        given(passwordEncoder.matches(NEW_RAW_PASSWORD, "{bcrypt}hash-1")).willReturn(false);
        given(passwordEncoder.matches(NEW_RAW_PASSWORD, "{bcrypt}hash-2")).willReturn(true);

        // when & then
        assertThat(passwordService.isPasswordReused(account, NEW_RAW_PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("현재 비밀번호·최근 이력 모두와 다르면 재사용이 아니다")
    void isPasswordReused_noMatch_returnsFalse() {
        // given
        given(passwordEncoder.matches(eq(NEW_RAW_PASSWORD), eq(CURRENT_ENCODED_PASSWORD))).willReturn(false);
        given(passwordHistoryRepository.findRecentByAccountId(ACCOUNT_ID, 5))
                .willReturn(List.of(PasswordHistory.builder().passwordHash("{bcrypt}hash-1").build()));
        given(passwordEncoder.matches(eq(NEW_RAW_PASSWORD), eq("{bcrypt}hash-1"))).willReturn(false);

        // when & then
        assertThat(passwordService.isPasswordReused(account, NEW_RAW_PASSWORD)).isFalse();
        verify(passwordHistoryRepository).findRecentByAccountId(ACCOUNT_ID, 5);
    }

    @Test
    @DisplayName("이력이 없는 계정은 현재 비밀번호만 비교한다")
    void isPasswordReused_noHistory_returnsFalse() {
        // given
        given(passwordEncoder.matches(NEW_RAW_PASSWORD, CURRENT_ENCODED_PASSWORD)).willReturn(false);
        given(passwordHistoryRepository.findRecentByAccountId(ACCOUNT_ID, 5)).willReturn(List.of());

        // when & then
        assertThat(passwordService.isPasswordReused(account, NEW_RAW_PASSWORD)).isFalse();
    }
}
