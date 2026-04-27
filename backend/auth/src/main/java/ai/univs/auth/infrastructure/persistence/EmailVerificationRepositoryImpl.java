package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.EmailVerificationType;
import ai.univs.auth.domain.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryImpl implements EmailVerificationRepository {

    private final EmailVerificationJpaRepository emailVerificationJpaRepository;

    @Override
    public EmailVerification save(EmailVerification emailVerification) {
        return emailVerificationJpaRepository.save(emailVerification);
    }

    @Override
    public Optional<EmailVerification> findByEmailAndVerifiedFalseAndType(String email, EmailVerificationType type) {
        return emailVerificationJpaRepository.findByEmailAndVerifiedFalseAndType(email, type);
    }

    @Override
    public Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email) {
        return emailVerificationJpaRepository.findTopByEmailOrderByCreatedAtDesc(email);
    }

    @Override
    public void deleteByEmailAndType(String email, EmailVerificationType type) {
        emailVerificationJpaRepository.deleteByEmailAndType(email, type);
    }
}
