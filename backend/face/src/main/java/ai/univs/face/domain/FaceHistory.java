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

    @OneToOne(mappedBy = "faceHistory")
    private FaceLiveness faceLiveness;

    @OneToOne(mappedBy = "faceHistory")
    private FaceMatch faceMatch;

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

    public void fail(String failureMessage, String managerUuid) {
        this.failureMessage = failureMessage;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
