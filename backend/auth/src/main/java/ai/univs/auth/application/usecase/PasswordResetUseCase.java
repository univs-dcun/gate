package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.EmailNotVerifiedException;
import ai.univs.auth.application.exception.InvalidVerificationCodeException;
import ai.univs.auth.application.exception.PasswordMismatchException;
import ai.univs.auth.shared.web.ctx.ClientRequestContextHolder;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.enums.PasswordResetMethod;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class PasswordResetUseCase {

    private final EmailVerificationRepository emailVerificationRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryRepository passwordHistoryRepository;

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void execute(String email, String newPassword, String passwordConfirm) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(EmailNotVerifiedException::new);

        // 비밀번호 초기화 메일 인증 확인
        if (!verification.isVerified()) {
            throw new EmailNotVerifiedException();
        }

        // 새로운 비밀번호, 컨펌 비밀번호 같은지 확인
        if (!newPassword.equals(passwordConfirm)) {
            throw new PasswordMismatchException();
        }

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(AccountNotFoundException::new);

        PasswordHistory history = PasswordHistory.builder()
                .accountId(account.getAccountId())
                .passwordHash(account.getPassword())
                .passwordResetMethod(PasswordResetMethod.EMAIL_RESET)
                .changedAt(LocalDateTime.now(ZoneOffset.UTC))
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build();
        passwordHistoryRepository.save(history);

        account.changePassword(passwordEncoder.encode(newPassword));
    }
}
