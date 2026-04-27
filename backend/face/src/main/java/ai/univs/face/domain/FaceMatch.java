package ai.univs.face.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "face_match")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "face_match_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "face_history_id")
    private FaceHistory faceHistory;

    private String faceId;
    private Double similarity;
    private Double threshold;
    @Enumerated(EnumType.STRING)
    private MatchType type;

    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;

    public static FaceMatch create(FaceHistory faceHistory,
                                   String faceId,
                                   Double similarity,
                                   Double threshold,
                                   MatchType type,
                                   String clientId
    ) {
        return FaceMatch.builder()
                .faceHistory(faceHistory)
                .faceId(faceId)
                .similarity(similarity)
                .threshold(threshold)
                .type(type)
                .createdBy(clientId)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .modifiedBy(clientId)
                .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public void updateFaceId(String faceId, String managerUuid) {
        this.faceId = faceId;
        this.modifiedBy = managerUuid;
        this.modifiedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
