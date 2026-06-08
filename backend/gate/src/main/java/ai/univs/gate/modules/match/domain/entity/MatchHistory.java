package ai.univs.gate.modules.match.domain.entity;

import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_history_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 10)
    private FeatureType featureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private MatchType matchType;

    private LocalDateTime matchTime;

    @NotNull
    @Column(name = "check_liveness")
    private Boolean checkLiveness;

    @NotNull
    @Column(name = "success")
    private Boolean success;

    @Column(name = "user_description")
    private String userDescription;

    @Column(name = "similarity", precision = 5, scale = 2)
    private BigDecimal similarity;

    @Column(name = "feature_id")
    private String featureId;

    @ColumnDefault("''")
    @Column(name = "matched_feature_id", length = 100)
    private String matchedFeatureId;

    @ColumnDefault("''")
    @Column(name = "feature_image_path", length = 100)
    private String featureImagePath;

    @ColumnDefault("''")
    @Column(name = "matched_feature_image_path", length = 100)
    private String matchedFeatureImagePath;

    @ColumnDefault("''")
    @Column(name = "failure_type", length = 100)
    private String failureType;

    @NotNull
    @Column(name = "transaction_uuid", nullable = false, length = 36)
    private String transactionUuid;

    @Column(name = "consent_snapshot")
    private Boolean consentSnapshot;

    public void updateFaceFeature(FaceFeature faceFeature) {
        this.featureId = faceFeature.getFeatureId();
        this.userDescription = faceFeature.getDescription();
        this.featureImagePath = faceFeature.getFeatureImagePath();
    }

    public void updatePalmFeature(PalmFeature palmFeature) {
        this.featureId = palmFeature.getFeatureId();
        this.userDescription = palmFeature.getDescription();
        this.featureImagePath = palmFeature.getFeatureImagePath();
    }

    public void success(FaceFeature faceFeature, BigDecimal similarity) {
        this.success = true;
        this.similarity = toPercent(similarity);
        updateFaceFeature(faceFeature);
    }

    public void success(PalmFeature palmFeature, BigDecimal similarity) {
        this.success = true;
        updatePalmFeature(palmFeature);
        this.similarity = toPercent(similarity);
    }

    public void successById(BigDecimal similarity) {
        this.success = true;
        this.similarity = toPercent(similarity);
    }

    // 1:1 (이미지:이미지) 매칭은 성공해도 사용자 정보를 포함하지 않습니다.
    public void success(BigDecimal similarity) {
        this.success = true;
        this.featureId = null;
        this.userDescription = "";
        this.similarity = toPercent(similarity);
    }

    public void fail(BigDecimal similarity, String failureType) {
        this.similarity = toPercent(similarity);
        this.failureType = failureType;
    }

    private BigDecimal toPercent(BigDecimal similarity) {
        if (similarity == null) return null;
        return similarity.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}