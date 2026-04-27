package ai.univs.auth.domain.entity;

import ai.univs.auth.domain.enums.PasswordResetMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PasswordResetMethod passwordResetMethod;

    private LocalDateTime changedAt;

    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;
}
