package ai.univs.palm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "palm_liveness")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalmLiveness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "palm_liveness_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "palm_history_id")
    private PalmHistory palmHistory;

    private boolean performed;
    private boolean passed;
    private double score;

    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;
}
