package ai.univs.auth.application.service;

import ai.univs.auth.domain.entity.PasswordHistory;
import ai.univs.auth.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean isPasswordReused(Long accountId, String newPassword) {
        Optional<PasswordHistory> findPasswordHistory =
                passwordHistoryRepository.findTop1ByAccountIdOrderByChangedAtDesc(accountId);

        if (findPasswordHistory.isPresent()) {
            String hashPassword = findPasswordHistory.get().getPasswordHash();
            return passwordEncoder.matches(newPassword, hashPassword);
        }

        return false;
    }
}
