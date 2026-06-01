package ai.univs.palm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "palm_match")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalmMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "palm_match_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "palm_history_id")
    private PalmHistory palmHistory;

    private String palmId;
    private Double similarity;
    private Double threshold;
    @Enumerated(EnumType.STRING)
    private MatchType type;

    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;

    public static PalmMatch create(PalmHistory palmHistory,
                                   String palmId,
                                   Double similarity,
                                   Double threshold,
                                   MatchType type,
                                   String clientId
    ) {
        return PalmMatch.builder()
                .palmHistory(palmHistory)
                .palmId(palmId)
                .similarity(similarity)
                .threshold(threshold)
                .type(type)
                .createdBy(clientId)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .modifiedBy(clientId)
                .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public void updateFaceId(String palmId, String managerUuid) {
        this.palmId = palmId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
