package ai.univs.face.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "face_liveness")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceLiveness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "face_liveness_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "face_history_id")
    private FaceHistory faceHistory;

    private String probability;
    private int prdioction;
    private String prdioctionDesc;
    private String quality;
    private String threshold;

    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;

    /** 롤백된 트랜잭션이 써 둔 IDENTITY id 를 지운다 (UG-363) — {@link FaceHistory#discardRolledBackId} 와 같은 이유. */
    public void discardRolledBackId() {
        this.id = null;
    }
}
