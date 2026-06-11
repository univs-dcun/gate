package ai.univs.gate.modules.project.domain.entity;

import ai.univs.gate.shared.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "project_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectSettings extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "consent_enabled", nullable = false)
    private Boolean consentEnabled;

    @Column(name = "consent_agreed_at")
    private LocalDateTime consentAgreedAt;

    public void updateConsentSettings(Boolean enabled) {
        this.consentEnabled = enabled;
        if (enabled) {
            this.consentAgreedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

}
