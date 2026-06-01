package ai.univs.palm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "palm_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalmHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "palm_history_id")
    private Long id;

    @OneToOne(mappedBy = "palmHistory")
    private PalmLiveness palmLiveness;

    @OneToOne(mappedBy = "palmHistory")
    private PalmMatch palmMatch;

    private String transactionUuid;
    @Enumerated(EnumType.STRING)
    private ActionType type;
    private String palmId;
    private boolean result;

    private String failureMessage;

    private boolean checkLiveness;

    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;

    public static PalmHistory create(
            ActionType actionType,
            String palmId,
            String transactionUuid,
            String clientId,
            boolean checkLiveness
    ) {
        return PalmHistory.builder()
                .transactionUuid(transactionUuid)
                .type(actionType)
                .palmId(palmId)
                .checkLiveness(checkLiveness)
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

    public void successRegister(boolean result, String palmId, String managerUuid) {
        this.result = result;
        this.palmId = palmId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successUpdate(boolean result, String palmId, String managerUuid) {
        this.result = result;
        this.palmId = palmId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void successDelete(boolean result, String palmId, String managerUuid) {
        this.result = result;
        this.palmId = palmId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void fail(String failureMessage, String managerUuid) {
        this.failureMessage = failureMessage;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
