package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.EmailVerificationType;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import ai.univs.auth.support.mail.MailService;
import ai.univs.auth.support.message.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendResetPasswordCodeUseCase {

    private final AccountRepository accountRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final MessageService messageService;

    private static final int CODE_EXPIRY_MINUTES = 5;

    @Transactional
    public void execute(String email) {
        log.info("Send password reset code, email: {}", email);

        accountRepository.findByEmail(email)
                .orElseThrow(AccountNotFoundException::new);

        emailVerificationRepository.deleteByEmailAndType(email, EmailVerificationType.PASSWORD_RESET);

        String verificationCode = generateVerificationCode();

        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .type(EmailVerificationType.PASSWORD_RESET)
                .verificationCode(passwordEncoder.encode(verificationCode))
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(CODE_EXPIRY_MINUTES))
                .verified(false)
                .attempts(0)
                .build();
        emailVerificationRepository.save(verification);

        mailService.send(
                email,
                messageService.getMessage("RESET_PASSWORD_SUBJECT"),
                "request_password_reset",
                Map.of("verificationCode", verificationCode),
                LocaleContextHolder.getLocale());
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = 10000000 + random.nextInt(90000000);
        return String.valueOf(code);
    }
}
