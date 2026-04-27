package ai.univs.gate.modules.api_key.domain.entity;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "api_key_id")
    private Long id;

    @JoinColumn(name = "project_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Project project;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}

