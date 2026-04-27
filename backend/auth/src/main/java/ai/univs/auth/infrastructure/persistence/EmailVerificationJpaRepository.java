package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.EmailVerification;
import ai.univs.auth.domain.enums.EmailVerificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationJpaRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmailAndVerifiedFalseAndType(String email, EmailVerificationType type);

    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmailAndType(String email, EmailVerificationType type);
}
