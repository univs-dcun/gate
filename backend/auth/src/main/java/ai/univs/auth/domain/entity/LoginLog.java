package ai.univs.auth.domain.entity;

import ai.univs.auth.domain.enums.LoginStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginStatus loginStatus;

    private String attemptedEmail;

    @Column(nullable = false)
    private LocalDateTime loginAt;

    @Column(length = 45)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;
}
