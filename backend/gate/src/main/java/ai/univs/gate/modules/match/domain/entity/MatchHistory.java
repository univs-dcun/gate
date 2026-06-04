package ai.univs.gate.modules.match.domain.entity;

import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.enums.MediaType;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.project.domain.entity.Project;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Column(name = "media_id")
    private Long mediaId;

    @Column(name = "face_id")
    private String faceId;

    @Column(name = "user_description")
    private String userDescription;

    @Column(name = "username", length = 255)
    private String username;

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

    @Column(name = "consent_snapshot")
    private Boolean consentSnapshot;

    public void updateFaceMedia(FaceMedia faceMedia) {
        this.mediaId = faceMedia.getId();
        this.faceId = faceMedia.getFaceId();
        this.userDescription = faceMedia.getDescription();
        this.username = faceMedia.getUsername();
        this.faceImagePath = faceMedia.getFaceImagePath();
    }

    public void updatePalmMedia(PalmMedia palmMedia) {
        this.mediaId = palmMedia.getId();
        this.faceId = palmMedia.getPalmId();
        this.userDescription = palmMedia.getDescription();
        this.username = palmMedia.getUsername();
        this.faceImagePath = palmMedia.getPalmImagePath();
    }

    public void success(FaceMedia faceMedia, BigDecimal similarity) {
        this.success = true;
        updateFaceMedia(faceMedia);
        this.similarity = toPercent(similarity);
    }

    public void success(PalmMedia palmMedia, BigDecimal similarity) {
        this.success = true;
        updatePalmMedia(palmMedia);
        this.similarity = toPercent(similarity);
    }

    public void successById(BigDecimal similarity) {
        this.success = true;
        this.similarity = toPercent(similarity);
    }

    // 1:1 (이미지:이미지) 매칭은 성공해도 사용자 정보를 포함하지 않습니다.
    public void success(BigDecimal similarity) {
        this.success = true;
        this.mediaId = null;
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