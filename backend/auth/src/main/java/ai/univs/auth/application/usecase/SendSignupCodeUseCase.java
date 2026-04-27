package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.DuplicateEmailException;
import ai.univs.auth.application.result.SendEmailVerificationCodeResult;
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
public class SendSignupCodeUseCase {

    private final EmailVerificationRepository emailVerificationRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final MessageService messageService;

    private static final int CODE_EXPIRY_MINUTES = 5;

    @Transactional
    public SendEmailVerificationCodeResult execute(String email) {
        log.info("Send signup verification code, email: {}", email);

        // 사용중인 이메일 체크
        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        // 사용자가 인증 코드를 여러번 요청할 경우 이전에 발급된 코드를 제거하기 위한 용도
        emailVerificationRepository.deleteByEmailAndType(email, EmailVerificationType.SIGNUP);

        // todo. 계정을 직접 생성하고 전달해야되는 케이스에 사용. 추후 제거 예정
        // 이메일 인증 코드 생성
        String verificationCode = generateVerificationCode();
        log.info("Generated verification code: {}", verificationCode);

        // 이메일 인증 요청 정보 저장
        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .type(EmailVerificationType.SIGNUP)
                .verificationCode(passwordEncoder.encode(verificationCode))
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(CODE_EXPIRY_MINUTES))
                .verified(false)
                .attempts(0)
                .build();
        emailVerificationRepository.save(verification);

        // 사용자 이메일 확인을 위한 인증용 메일 전송
        mailService.send(
                email,
                messageService.getMessage("JOIN_SUBJECT"),
                "send_email_verification_code",
                Map.of("verificationCode", verificationCode),
                LocaleContextHolder.getLocale());

        return new SendEmailVerificationCodeResult(email, verification.getExpiresAt());
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = 10000000 + random.nextInt(90000000);
        return String.valueOf(code);
    }
}
