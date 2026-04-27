package ai.univs.gate.modules.webhook.domain.entity;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "webhook_configs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_config_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    @Column(name = "demo_enabled", nullable = false)
    private Boolean demoEnabled;

    @Column(name = "sdk_enabled", nullable = false)
    private Boolean sdkEnabled;

    @Column(name = "api_enabled", nullable = false)
    private Boolean apiEnabled;

    public void update(String webhookUrl,
                       Boolean demoEnabled,
                       Boolean sdkEnabled,
                       Boolean apiEnabled
    ) {
        this.webhookUrl = webhookUrl;
        this.demoEnabled = demoEnabled;
        this.sdkEnabled = sdkEnabled;
        this.apiEnabled = apiEnabled;
    }
}
