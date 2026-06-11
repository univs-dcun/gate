package ai.univs.gate.modules.project.domain.entity;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "project_liveness_settings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_project_liveness",
        columnNames = {"project_settings_id", "module_type", "operation"}
    )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectLivenessSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "liveness_setting_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_settings_id", nullable = false)
    private ProjectSettings projectSettings;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 10)
    private FeatureType moduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    private LivenessOperation operation;

    @Column(nullable = false)
    private Boolean enabled;

    public void updateEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
