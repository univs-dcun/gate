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

    // 인증 완료 후 소비(회원가입/비밀번호 재설정)까지 허용되는 유효 시간.
    // 코드 입력 만료(expiresAt)와 별개로, 인증만 해두고 방치된 레코드가 무기한 사용되는 것을 막는다.
    private static final long CONSUMPTION_VALID_MINUTES = 30;

    public boolean isExpired() {
        return LocalDateTime.now(ZoneOffset.UTC).isAfter(expiresAt);
    }

    public boolean isVerified() {
        return verified != null && verified;
    }

    // 소비 시점 검증: 인증 완료 + 인증 후 유효 시간 이내여야 한다.
    public boolean isUsableForConsumption() {
        return isVerified()
                && verifiedAt != null
                && LocalDateTime.now(ZoneOffset.UTC).isBefore(verifiedAt.plusMinutes(CONSUMPTION_VALID_MINUTES));
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
