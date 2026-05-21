package ai.univs.gate.modules.project.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "consent_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "end_user_identifier", nullable = false)
    private Long endUserIdentifier;

    @Column(name = "consent_type", nullable = false)
    private String consentType;

    @Column(name = "agreed", nullable = false)
    private Boolean agreed;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
