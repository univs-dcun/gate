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

    /**
     * 하위 서비스 실패의 상태 코드 (UG-294).
     *
     * <p>{@code failure_type} 은 하위 서비스 실패를 전부 {@code INTERNAL_SERVER_ERROR} 하나로
     * 적는다 — 502 도, 연결 거부도, 본문 디코딩 실패도, HTTP 200 인데 {@code data} 가 빈 것도
     * 같은 값이다. 장애를 조사할 때 원인을 가를 수 없었다.
     *
     * <p>{@code failure_type} 을 세분화하지 않은 이유는 그 값이 <b>클라이언트 응답에 나가는
     * 값</b>이기 때문이다. 새 값을 만들면 고객이 보는 값이 늘고 i18n 리소스도 함께 늘어난다.
     * 이 컬럼은 응답에 넣지 않는다 — 조사용이다.
     *
     * <p>{@code 0} 은 응답을 받지 못했다는 뜻이다
     * ({@link ai.univs.gate.shared.exception.RemoteCallException#NO_RESPONSE}).
     * {@code null} 은 하위 서비스 실패가 아니거나 이 컬럼이 생기기 전의 행이다.
     */
    @Column(name = "upstream_status")
    private Integer upstreamStatus;

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

    /**
     * 하위 서비스 실패로 끝났을 때 (UG-294).
     *
     * <p>{@code failureType} 은 기존과 같은 값을 그대로 쓰고({@code INTERNAL_SERVER_ERROR}),
     * 원인 구분은 {@link #upstreamStatus} 에 남긴다. 응답 계약은 건드리지 않는다.
     */
    public void failUpstream(String failureType, int upstreamStatus) {
        this.similarity = toPercent(BigDecimal.ZERO);
        this.failureType = failureType;
        this.upstreamStatus = upstreamStatus;
    }

    private BigDecimal toPercent(BigDecimal similarity) {
        if (similarity == null) return null;
        return similarity.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
