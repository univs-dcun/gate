package ai.univs.gate.modules.match.domain.entity;

import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.user.domain.entity.User;
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
    @Column(name = "match_type", nullable = false)
    private MatchType matchType;

    private LocalDateTime matchTime;

    @NotNull
    @Column(name = "check_liveness")
    private Boolean checkLiveness;

    @NotNull
    @Column(name = "success")
    private Boolean success;

    @ColumnDefault("''")
    @Column(name = "match_face_id", length = 100)
    private String matchFaceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "face_id")
    private String faceId;

    @Column(name = "user_description")
    private String userDescription;

    @Column(name = "similarity", precision = 5, scale = 2)
    private BigDecimal similarity;

    @ColumnDefault("''")
    @Column(name = "face_image_path", length = 100)
    private String faceImagePath;

    @ColumnDefault("''")
    @Column(name = "match_face_image_path", length = 100)
    private String matchFaceImagePath;

    @ColumnDefault("''")
    @Column(name = "failure_type", length = 100)
    private String failureType;

    @NotNull
    @Column(name = "transaction_uuid", nullable = false, length = 36)
    private String transactionUuid;

    public void success(User user, BigDecimal similarity) {
        this.success = true;
        this.userId = user.getId();
        this.faceId = user.getFaceId();
        this.userDescription = user.getDescription();
        this.faceImagePath = user.getFaceImagePath();
        this.similarity = toPercent(similarity);
    }

    // 1:1 (이미지:이미지) 매칭은 성공해도 사용자 정보를 포함하지 않습니다.
    public void success(BigDecimal similarity) {
        this.success = true;
        this.userId = null;
        this.faceId = "";
        this.userDescription = "";
        this.similarity = toPercent(similarity);
    }

    public void fail(BigDecimal similarity, String failureType) {
        this.similarity = toPercent(similarity);
        this.failureType = failureType;
    }

    private BigDecimal toPercent(BigDecimal similarity) {
        if (similarity == null) return null;
        return similarity.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}