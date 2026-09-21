package ai.univs.gate.modules.feature.domain.entity;

import ai.univs.gate.modules.feature.domain.enums.FeatureActionType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특징점 등록·삭제 이력 (UG-325).
 *
 * <p><b>왜 {@code match_history} 가 아닌가.</b> 그 테이블의 컬럼 15개 중 등록 행에 의미 있는 것은
 * 절반이고 삭제 행에는 4개다 — {@code similarity}, {@code matched_feature_image_path},
 * {@code match_time} 이 전부 매칭 전용이다. 대시보드도 그래서 등록을 {@code biometric_feature}
 * 를 세는 것으로 우회했는데, 그 결과 삭제하면 과거 달의 등록 수가 소급해서 줄었다
 * (현재 잔존 수를 "등록" 이라 부른 것). 이력은 append-only 여야 그 문제가 없다.
 *
 * <p><b>FK 대신 스냅샷.</b> {@code feature_seq}·{@code feature_id}·메모·이미지 경로·
 * {@code external_key} 를 행에 복사해 둔다. {@code biometric_feature} 는 지금은 소프트 삭제지만,
 * 나중에 하드 삭제나 보존기간 정리를 들이는 순간 FK 만 가진 이력은 고아가 된다. 은행권에서
 * "무엇을 언제 지웠나" 는 대상이 사라진 뒤에 묻는 질문이다.
 *
 * <p><b>실패도 남긴다.</b> 행을 호출 <b>전에</b> 저장하고 결과로 갱신한다. face-service 의
 * {@code DeleteUseCase} 와 같은 순서다. 성공한 삭제보다 실패한 삭제 시도가 감사에서 더 중요할
 * 때가 있다. 그래서 쓰는 쪽은 {@code noRollbackFor} 로 하위 서비스 실패에도 이 행을 커밋해야
 * 한다 (UG-280 과 같은 이유).
 *
 * <p><b>{@code transaction_uuid}.</b> 등록은 요청의 것을, 삭제는 새로 만든 것을 쓴다. 나중에
 * "수정 = 삭제 + 등록" 을 도입하면 두 행이 이 값으로 묶인다 — 그때 테이블을 다시 고치지 않게
 * 지금부터 채운다.
 */
@Entity
@Table(name = "feature_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FeatureHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_history_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 10)
    private FeatureType featureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private FeatureActionType actionType;

    @Column(name = "success", nullable = false)
    private boolean success;

    /** 등록 시점에 라이브니스를 검사했는가. 삭제에는 의미가 없어 false 로 둔다. */
    @Column(name = "check_liveness", nullable = false)
    private boolean checkLiveness;

    @Column(name = "failure_type", length = 100)
    private String failureType;

    @Column(name = "transaction_uuid", nullable = false, length = 36)
    private String transactionUuid;

    // ── 스냅샷 ─────────────────────────────────────────────────────────────────────

    /** {@code biometric_feature.biometric_feature_id}. 화면의 "일련번호". */
    @Column(name = "feature_seq")
    private Long featureSeq;

    @Column(name = "feature_id", length = 255)
    private String featureId;

    @Column(name = "user_description", length = 1000)
    private String userDescription;

    @Column(name = "feature_image_path", length = 255)
    private String featureImagePath;

    @Column(name = "consent_snapshot")
    private Boolean consentSnapshot;

    @Column(name = "external_key", length = 255)
    private String externalKey;

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

    // ── 팩토리 ─────────────────────────────────────────────────────────────────────

    /**
     * 등록 시도. 하위 서비스를 호출하기 <b>전에</b> 저장한다 — 실패하면 {@link #fail} 로 남긴다.
     * 특징점은 아직 없으므로 스냅샷은 {@link #successRegister} 에서 채운다.
     */
    public static FeatureHistory register(Project project,
                                          FeatureType featureType,
                                          boolean checkLiveness,
                                          String featureImagePath,
                                          String transactionUuid,
                                          Boolean consentSnapshot) {
        return FeatureHistory.builder()
                .project(project)
                .featureType(featureType)
                .actionType(FeatureActionType.REGISTER)
                .success(false)
                .checkLiveness(checkLiveness)
                .featureImagePath(featureImagePath)
                .transactionUuid(transactionUuid)
                .consentSnapshot(consentSnapshot)
                .build();
    }

    /**
     * 삭제 시도. 대상이 아직 살아 있는 시점이라 스냅샷을 지금 뜬다 — 성공하면 {@code is_deleted}
     * 가 켜지고, 나중에 행이 정리되면 이 값들이 유일한 기록이다.
     */
    public static FeatureHistory delete(Project project, BiometricFeature target, String transactionUuid) {
        return FeatureHistory.builder()
                .project(project)
                .featureType(target.getType())
                .actionType(FeatureActionType.DELETE)
                .success(false)
                .checkLiveness(false)
                .transactionUuid(transactionUuid)
                .featureSeq(target.getId())
                .featureId(target.getFeatureId())
                .userDescription(target.getDescription())
                .featureImagePath(target.getFeatureImagePath())
                .externalKey(target.getExternalKey())
                .build();
    }

    // ── 상태 전이 ───────────────────────────────────────────────────────────────────

    public void successRegister(BiometricFeature saved) {
        this.success = true;
        this.featureSeq = saved.getId();
        this.featureId = saved.getFeatureId();
        this.userDescription = saved.getDescription();
        this.featureImagePath = saved.getFeatureImagePath();
        this.externalKey = saved.getExternalKey();
    }

    public void successDelete() {
        this.success = true;
    }

    public void fail(String failureType) {
        this.failureType = failureType;
    }
}
