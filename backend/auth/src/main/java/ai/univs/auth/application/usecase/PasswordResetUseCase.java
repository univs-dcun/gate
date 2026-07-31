package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.EmailNotVerifiedException;
import ai.univs.auth.application.exception.InvalidVerificationCodeException;
import ai.univs.auth.application.exception.PasswordMismatchException;
import ai.univs.auth.application.exception.PasswordReusedException;
import ai.univs.auth.application.service.PasswordService;
import ai.univs.auth.shared.web.ctx.ClientRequestContextHolder;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.enums.EmailVerificationType;
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
    private final PasswordService passwordService;

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void execute(String email, String newPassword, String passwordConfirm) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndTypeOrderByCreatedAtDesc(email, EmailVerificationType.PASSWORD_RESET)
                .orElseThrow(EmailNotVerifiedException::new);

        // 비밀번호 초기화 메일 인증 확인 (인증 완료 + 인증 후 유효 시간 이내)
        if (!verification.isUsableForConsumption()) {
            throw new EmailNotVerifiedException();
        }

        // 새로운 비밀번호, 컨펌 비밀번호 같은지 확인
        if (!newPassword.equals(passwordConfirm)) {
            throw new PasswordMismatchException();
        }

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(AccountNotFoundException::new);

        // 기존에 사용한 비밀번호와 같은 비밀번호로 재설정 시도하는지 확인
        if (passwordService.isPasswordReused(account, newPassword)) {
            throw new PasswordReusedException();
        }

        // 이력에는 변경으로 적용되는 새 비밀번호 해시를 저장 (변경 경로와 동일 규약)
        String encodedPassword = passwordEncoder.encode(newPassword);
        PasswordHistory history = PasswordHistory.builder()
                .accountId(account.getAccountId())
                .passwordHash(encodedPassword)
                .passwordResetMethod(PasswordResetMethod.EMAIL_RESET)
                .changedAt(LocalDateTime.now(ZoneOffset.UTC))
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build();
        passwordHistoryRepository.save(history);

        account.changePassword(encodedPassword);

        // 사용한 인증 레코드 소진 — 동일 인증으로 반복 재설정 불가
        emailVerificationRepository.deleteByEmailAndType(email, EmailVerificationType.PASSWORD_RESET);
    }
}
