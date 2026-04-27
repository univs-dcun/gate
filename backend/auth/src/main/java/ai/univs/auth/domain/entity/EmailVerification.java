package ai.univs.auth.domain.entity;

import ai.univs.auth.domain.enums.EmailVerificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long verificationId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmailVerificationType type;

    @Column(nullable = false)
    private String verificationCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean verified;

    private LocalDateTime verifiedAt;

    @Column(nullable = false)
    private Integer attempts;

    public boolean isExpired() {
        return LocalDateTime.now(ZoneOffset.UTC).isAfter(expiresAt);
    }

    public boolean isVerified() {
        return verified != null && verified;
    }

    public boolean canAttempt() {
        return attempts < 3;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsVerified() {
        this.verified = true;
        this.verifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
