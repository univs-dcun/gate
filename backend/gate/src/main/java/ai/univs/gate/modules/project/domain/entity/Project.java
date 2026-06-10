package ai.univs.gate.modules.project.domain.entity;

import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "project_name", nullable = false, length = 100)
    private String projectName;

    @Column(name = "project_description", length = 500)
    private String projectDescription;

    @Column(name = "branch_name", nullable = false, unique = true, updatable = false)
    private String branchName;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "color_tag", length = 50)
    private String colorTag;

    public void updateInfo(String projectName, String projectDescription, String colorTag) {
        if (projectName != null) this.projectName = projectName;
        if (projectDescription != null) this.projectDescription = projectDescription;
        if (colorTag != null) this.colorTag = colorTag;
    }

    public void activate() {
        this.status = ProjectStatus.ACTIVE;
    }

    public void delete() {
        this.isDeleted = false;
        this.status = ProjectStatus.INACTIVE;
    }
}