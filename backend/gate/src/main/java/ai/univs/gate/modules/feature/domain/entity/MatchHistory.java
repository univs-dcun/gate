package ai.univs.gate.modules.feature.domain.entity;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
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

    @Column(name = "feature_seq")
    private Long featureSeq;

    /**
     * UG-328: 인증 시도와 특징점 사건이 공유하는 사건 시퀀스 — 로그 상세의 "일련번호". DB 기본값
     * ({@code activity_seq.NEXTVAL}, V29)이 채우므로 애플리케이션은 쓰지 않는다. H2 슬라이스는
     * {@code @ColumnDefault} 로 같은 기본값을 만들고 시퀀스는 test resources 의 schema.sql 이 만든다.
     * save() 직후 메모리 값은 null 이다(재조회 없음) — 응답에 실어야 할 일이 생기면
     * {@code @Generated(event = INSERT)} 로 바꿔 INSERT 뒤 읽어 오게 한다.
     */
    @Column(name = "activity_seq", insertable = false, updatable = false)
    @ColumnDefault("nextval('activity_seq')")
    private Long activitySeq;

    public void updateBiometricFeature(BiometricFeature biometricFeature) {
        this.featureId = biometricFeature.getFeatureId();
        this.userDescription = biometricFeature.getDescription();
        this.featureImagePath = biometricFeature.getFeatureImagePath();
        this.featureSeq = biometricFeature.getId();
    }

    public void success(BiometricFeature biometricFeature, BigDecimal similarity) {
        this.success = true;
        this.similarity = toPercent(similarity);
        updateBiometricFeature(biometricFeature);
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
