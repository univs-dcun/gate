package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.EmailVerificationType;

import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification emailVerification);

    Optional<EmailVerification> findByEmailAndVerifiedFalseAndType(String email, EmailVerificationType type);

    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmailAndType(String email, EmailVerificationType type);
}
