package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.InvalidVerificationCodeException;
import ai.univs.auth.application.exception.TooManyAttemptsException;
import ai.univs.auth.application.exception.VerificationExpiredException;
import ai.univs.auth.application.exception.VerificationNotFoundException;
import ai.univs.auth.application.result.VerifyEmailCodeResult;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.EmailVerificationType;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyResetPasswordCodeUseCase {

    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public VerifyEmailCodeResult execute(String email, String code) {
        log.info("Verify password reset code: email={}", email);

        EmailVerification verification = emailVerificationRepository
                .findByEmailAndVerifiedFalseAndType(email, EmailVerificationType.PASSWORD_RESET)
                .orElseThrow(VerificationNotFoundException::new);

        // 코드 기간 만료 확인
        if (verification.isExpired()) {
            throw new VerificationExpiredException();
        }

        // 검증 시도 횟수 확인
        if (!verification.canAttempt()) {
            throw new TooManyAttemptsException();
        }

        // 코드 확인
        if (!passwordEncoder.matches(code, verification.getVerificationCode())) {
            verification.incrementAttempts();
            throw new InvalidVerificationCodeException();
        }

        verification.markAsVerified();

        log.info("Password reset code verified: email={}", email);
        return new VerifyEmailCodeResult(true);
    }
}
