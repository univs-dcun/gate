package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.InvalidPasswordException;
import ai.univs.auth.application.exception.PasswordReusedException;
import ai.univs.auth.application.input.PasswordChangeInput;
import ai.univs.auth.application.result.PasswordChangeResult;
import ai.univs.auth.shared.web.ctx.ClientRequestContextHolder;
import ai.univs.auth.application.service.PasswordService;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.enums.PasswordResetMethod;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class PasswordChangeUseCase {

    private final AccountRepository accountRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordService passwordService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PasswordChangeResult execute(PasswordChangeInput input) {
        Account account = accountRepository.findById(input.accountId())
                .orElseThrow(AccountNotFoundException::new);

        // 비밀번호 확인
        if (!passwordEncoder.matches(input.password(), account.getPassword())) {
            throw new InvalidPasswordException();
        }

        // 기존에 사용한 비밀번호와 같은 비밀번호로 변경 시도하는지 확인
        if (passwordService.isPasswordReused(input.accountId(), input.newPassword())) {
            throw new PasswordReusedException();
        }

        String encodedPassword = passwordEncoder.encode(input.newPassword());
        PasswordHistory history = PasswordHistory.builder()
                .accountId(account.getAccountId())
                .passwordHash(encodedPassword)
                .passwordResetMethod(PasswordResetMethod.USER_CHANGE)
                .changedAt(LocalDateTime.now(ZoneOffset.UTC))
                .ipAddress(ClientRequestContextHolder.getIpAddress())
                .userAgent(ClientRequestContextHolder.getUserAgent())
                .build();
        passwordHistoryRepository.save(history);

        account.changePassword(encodedPassword);

        return new PasswordChangeResult(account.getAccountId());
    }
}
