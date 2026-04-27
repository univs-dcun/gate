package ai.univs.gate.modules.user.domain.entity;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    @Setter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String faceId;
    private String faceImagePath;
    private String description;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    private String transactionUuid;

    public void updateFaceImagePath(String faceImagePath) {
        this.faceImagePath = faceImagePath;
    }

    public void updateUserInfo(String faceId, String description) {
        if (StringUtils.hasText(faceId)) this.faceId = faceId;
        if (StringUtils.hasText(description)) this.description = description;
    }

    public void delete() {
        this.isDeleted = true;
    }
}
