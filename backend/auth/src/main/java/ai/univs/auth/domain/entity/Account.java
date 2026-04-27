package ai.univs.auth.domain.entity;

import ai.univs.auth.domain.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private Integer failedLoginAttempts;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    @Column(length = 45)
    private String lastLoginIp;

    private LocalDateTime passwordChangedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isLocked() {
        if (status == AccountStatus.LOCKED) {
            if (lockedUntil != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(lockedUntil)) {
                this.status = AccountStatus.ACTIVE;
                this.failedLoginAttempts = 0;
                this.lockedUntil = null;
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.status = AccountStatus.LOCKED;
            this.lockedUntil = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30);
        }
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void updateLastLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now(ZoneOffset.UTC);
        this.lastLoginIp = ipAddress;
    }

    public void changePassword(String password) {
        this.password = password;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
