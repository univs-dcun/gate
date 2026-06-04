package ai.univs.gate.modules.palm_media.domain.entity;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "palm_media")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalmMedia extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "palm_media_id")
    @Setter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String palmId;
    private String palmImagePath;
    private String description;

    @Column(length = 255)
    private String username;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    private String transactionUuid;

    @Column(name = "external_key")
    private String externalKey;

    public void updatePalmImagePath(String palmImagePath) {
        this.palmImagePath = palmImagePath;
    }

    public void updatePalmId(String palmId) {
        this.palmId = palmId;
    }

    public void updateInfo(String description, String username) {
        if (StringUtils.hasText(description)) this.description = description;
        if (StringUtils.hasText(username)) this.username = username;
    }

    public void delete() {
        this.isDeleted = true;
    }
}
