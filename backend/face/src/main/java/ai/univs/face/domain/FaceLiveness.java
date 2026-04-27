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
}
