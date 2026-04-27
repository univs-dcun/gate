package ai.univs.auth.application.usecase;

import ai.univs.auth.application.event.AccountCreatedEvent;
import ai.univs.auth.application.exception.DuplicateEmailException;
import ai.univs.auth.application.exception.EmailNotVerifiedException;
import ai.univs.auth.application.exception.PasswordMismatchException;
import ai.univs.auth.application.input.SignupInput;
import ai.univs.auth.application.result.SignupResult;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignupUseCase {

    private final AccountRepository accountRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SignupResult execute(SignupInput input) {
        // 이메일 인증 요청 정보 조회
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(input.email())
                .orElseThrow(EmailNotVerifiedException::new);

        // 가입 메일 인증 확인
        if (!verification.isVerified()) {
            throw new EmailNotVerifiedException();
        }

        // 비밀번호 확인
        if (!input.isPasswordMatching()) {
            throw new PasswordMismatchException();
        }

        // 사용중인 이메일 확인
        if (accountRepository.existsByEmail(input.email())) {
            throw new DuplicateEmailException();
        }

        // 계정 생성
        Account account = Account.builder()
                .email(input.email())
                .password(passwordEncoder.encode(input.password()))
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        Account savedAccount = accountRepository.save(account);

        // 계정 생성 완료 후 Company init 이벤트 발행
        eventPublisher.publishEvent(new AccountCreatedEvent(savedAccount.getAccountId(), savedAccount.getEmail()));
        log.info("Account created: accountId={}, email={}", savedAccount.getAccountId(), savedAccount.getEmail());

        return SignupResult.of(savedAccount);
    }
}
