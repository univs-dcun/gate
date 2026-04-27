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

    @Column(name = "demo_enabled", nullable = false)
    private Boolean demoEnabled;

    @Column(name = "sdk_enabled", nullable = false)
    private Boolean sdkEnabled;

    @Column(name = "liveness_recording_enabled", nullable = false)
    private Boolean livenessRecordingEnabled;

    @Column(name = "liveness_identifying_enabled", nullable = false)
    private Boolean livenessIdentifyingEnabled;

    @Column(name = "liveness_verifying_enabled", nullable = false)
    private Boolean livenessVerifyingEnabled;

    public void updateConsentSettings(Boolean enabled) {
        this.consentEnabled = enabled;
        if (enabled) {
            this.consentAgreedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public void updateLivenessSettings(Boolean recording,
                                       Boolean matching,
                                       Boolean verification
    ) {
        if (recording != null) this.livenessRecordingEnabled = recording;
        if (matching != null) this.livenessIdentifyingEnabled = matching;
        if (verification != null) this.livenessVerifyingEnabled = verification;
    }

    public void updateDemoSettings(Boolean demoEnabled) {
        if (demoEnabled != null) this.demoEnabled = demoEnabled;
    }

    public void updateSdkSettings(Boolean sdkEnabled) {
        if (sdkEnabled != null) this.sdkEnabled = sdkEnabled;
    }
}
