package ai.univs.auth.application.service;

import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordService {

    // 현재 비밀번호 + 최근 이력 5건과 일치하면 재사용으로 판정
    private static final int REUSE_CHECK_HISTORY_SIZE = 5;

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean isPasswordReused(Account account, String newPassword) {
        if (passwordEncoder.matches(newPassword, account.getPassword())) {
            return true;
        }

        return passwordHistoryRepository
                .findRecentByAccountId(account.getAccountId(), REUSE_CHECK_HISTORY_SIZE)
                .stream()
                .anyMatch(history -> passwordEncoder.matches(newPassword, history.getPasswordHash()));
    }
}
