package ai.univs.gate.modules.project.domain.entity;

import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.enums.ProjectType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20)
    private ProjectType projectType;

    /**
     * 한 번 설정되면 변경 불가. updatable = false 로 DB 레벨에서도 강제.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "project_module_type", nullable = false, length = 20, updatable = false)
    private ProjectModuleType projectModuleType;

    /** External 타입일 때만 설정 가능. Standard 로 변경 시 자동 초기화. */
    @Column(name = "package_key", length = 99)
    private String packageKey;

    public void updateInfo(String projectName, String projectDescription) {
        if (projectName != null) this.projectName = projectName;
        if (projectDescription != null) this.projectDescription = projectDescription;
    }

    public void updateProjectType(ProjectType projectType) {
        this.projectType = projectType;
        if (projectType == ProjectType.STANDARD) {
            this.packageKey = null;
        }
    }

    public void updatePackageKey(String packageKey) {
        this.packageKey = packageKey;
    }

    public boolean isExternal() {
        return this.projectType == ProjectType.EXTERNAL;
    }

    public void activate() {
        this.status = ProjectStatus.ACTIVE;
    }

    public void delete() {
        this.isDeleted = false;
        this.status = ProjectStatus.INACTIVE;
    }
}