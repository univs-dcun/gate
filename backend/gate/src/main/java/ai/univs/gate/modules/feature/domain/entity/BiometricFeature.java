package ai.univs.gate.modules.feature.domain.entity;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "biometric_feature")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "biometric_feature_id")
    @Setter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private FeatureType type;

    private String featureId;
    private String featureImagePath;
    private String description;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    private String transactionUuid;

    @Column(name = "external_key")
    private String externalKey;

    public void updateFeatureImagePath(String featureImagePath) {
        this.featureImagePath = featureImagePath;
    }

    public void updateFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public void updateInfo(String description) {
        if (StringUtils.hasText(description)) this.description = description;
    }

    public void delete() {
        this.isDeleted = true;
    }
}
