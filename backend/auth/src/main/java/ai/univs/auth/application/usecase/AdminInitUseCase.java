package ai.univs.auth.application.usecase;

import ai.univs.auth.application.event.AccountCreatedEvent;
import ai.univs.auth.application.exception.AdminAlreadyInitializedException;
import ai.univs.auth.application.result.SignupResult;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.enums.AccountStatus;
import ai.univs.auth.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@Profile("onpremise")
@RequiredArgsConstructor
public class AdminInitUseCase {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SignupResult execute(String email, String password) {
        if (accountRepository.existsAny()) {
            throw new AdminAlreadyInitializedException();
        }

        Account account = Account.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        Account saved = accountRepository.save(account);

        eventPublisher.publishEvent(new AccountCreatedEvent(saved.getAccountId(), saved.getEmail()));
        log.info("On-premise admin account initialized: accountId={}, email={}", saved.getAccountId(), saved.getEmail());

        return SignupResult.of(saved);
    }
}
