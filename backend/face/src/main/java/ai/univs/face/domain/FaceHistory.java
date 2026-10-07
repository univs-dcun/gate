package ai.univs.face.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "face_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "face_history_id")
    private Long id;

    // UG-358: 역방향 @OneToOne(mappedBy) faceLiveness·faceMatch 를 지웠다. 아무도 읽지 않았고, 기본이 EAGER 라
    // 이력을 읽을 때마다(준영속 이력의 merge 포함) face_liveness·face_match 를 face_history_id 로 조인했다 — 그 컬럼에는
    // 인덱스가 없어(V1) 두 테이블을 풀스캔한다(50만 행 기준 19.5ms, 반박 리뷰 실측). 연관은 두 엔티티 쪽 FK 가 갖는다.

    private String transactionUuid;
    @Enumerated(EnumType.STRING)
    private ActionType type;
    private String faceId;
    private boolean result;

    private String failureMessage;

    private boolean checkLiveness;
    private boolean checkMultiFace;

    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;

    /**
     * 추출 중 받은 라이브니스 결과 — 아직 저장하지 않았다 (UG-358 2단계 반박 리뷰 M1).
     *
     * <p>예전에는 추출 직후 따로 저장해, 쓰기 요청 하나가 커넥션을 세 번(시작·라이브니스·결과) 기다렸다. 혼잡하면 각각
     * connection-timeout 까지 기다려 face 의 503 이 gate readTimeout(5초)을 넘겼다. 이제 결과 커밋({@code finish}·
     * {@code recordFailure})이 이력과 한 트랜잭션으로 저장한다 — 대기 지점이 하나 줄고, 라이브니스 행과 이력 결과가
     * 함께 커밋된다. 컬럼이 아니다.
     */
    @Transient
    private FaceLiveness pendingLiveness;

    public static FaceHistory create(
            ActionType actionType,
            String faceId,
            String transactionUuid,
            String clientId,
            boolean checkLiveness,
            boolean checkMultiFace
    ) {
        return FaceHistory.builder()
                .transactionUuid(transactionUuid)
                .type(actionType)
                .faceId(faceId)
                .checkLiveness(checkLiveness)
                .checkMultiFace(checkMultiFace)
                .result(false)
                .createdBy(clientId)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .modifiedBy(clientId)
                .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public void successExtract(boolean result, String managerUuid) {
        this.result = result;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successLiveness(boolean result, String managerUuid) {
        this.result = result;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successMatch(boolean result, String managerUuid) {
        this.result = result;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successRegister(boolean result, String faceId, String managerUuid) {
        this.result = result;
        this.faceId = faceId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successUpdate(boolean result, String faceId, String managerUuid) {
        this.result = result;
        this.faceId = faceId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successDelete(boolean result, String faceId, String managerUuid) {
        this.result = result;
        this.faceId = faceId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 결과를 커밋하지 못했다 (UG-358). 성공·미달로 정해 둔 상태를 되돌려 「결과 미기록 + 사유」로 남긴다 —
     * 「성공인데 실패 사유」나 「미달인데 결과 행 없음」처럼 섞인 상태가 남지 않게.
     */
    public void failUnrecorded(String failureMessage, String managerUuid) {
        this.result = false;
        fail(failureMessage, managerUuid);
    }

    /** 라이브니스 결과를 결과 커밋 때 함께 저장하도록 붙여 둔다. */
    public void attachLiveness(FaceLiveness faceLiveness) {
        this.pendingLiveness = faceLiveness;
    }

    /** 저장했으면 비운다 — 같은 행을 두 번 넣지 않게. */
    public void clearPendingLiveness() {
        this.pendingLiveness = null;
    }

    public void fail(String failureMessage, String managerUuid) {
        this.failureMessage = failureMessage;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
