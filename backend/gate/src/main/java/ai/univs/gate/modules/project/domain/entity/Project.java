package ai.univs.gate.modules.project.domain.entity;

import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    /**
     * 소프트 삭제 시각 (UG-303). 유예 기간을 재는 기준이다.
     *
     * <p>{@code null} 이면 삭제되지 않았거나, 이 컬럼이 생기기 전에 삭제된 행이다. 후자는
     * 삭제 시각을 알 수 없으므로 퍼지 대상에서 제외한다 — 임의로 채워 지우는 것보다 안전하다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "color_tag", length = 255)
    private String colorTag;

    public void updateInfo(String projectName, String projectDescription, String colorTag) {
        if (projectName != null) this.projectName = projectName;
        if (projectDescription != null) this.projectDescription = projectDescription;
        if (colorTag != null) this.colorTag = colorTag;
    }

    public void activate() {
        this.status = ProjectStatus.ACTIVE;
    }

    /**
     * 소프트 삭제 (UG-288).
     *
     * <p>이 메서드는 {@code isDeleted = false} 를 세팅하고 있었다. 컬럼 기본값이 {@code FALSE} 이므로
     * 아무 일도 하지 않는 줄이었고, 프로젝트 소프트 삭제는 한 번도 동작한 적이 없다. 삭제해도
     * 목록에 남고({@code ProjectDSLRepository} 가 {@code isDeleted.isFalse()} 로 거른다),
     * {@code findByIdAndIsDeletedFalse} 가 계속 찾아내고, 그 프로젝트의 API 키로 등록·매칭·이력
     * 조회가 전부 정상 동작했다.
     *
     * <p>{@code INACTIVE} 대신 {@link ProjectStatus#DELETED} 를 쓴다. 그 값은 정의만 되어 있고
     * 어디서도 쓰이지 않던 죽은 열거값이었다 — 원래 여기서 쓰려던 것으로 보이며, gate-web 도
     * {@code 'ACTIVE' | 'INACTIVE' | 'DELETED'} 로 이미 기대하고 있다. {@code INACTIVE} 는
     * "삭제는 아니지만 비활성" 자리로 비워 둔다.
     *
     * <p><b>UG-303: 삭제 시각을 함께 남긴다.</b> {@code deletedAt} 은 생체 데이터 정리의 유예
     * 기간을 재는 기준이다. {@code updated_at} 으로 근사할 수는 있지만 삭제 외의 갱신에도
     * 움직여 "삭제 후 N일" 의 기준이 되지 못한다.
     *
     * <p>이미 삭제된 프로젝트를 다시 삭제해도 시각을 덮어쓰지 않는다 — 덮어쓰면 유예가
     * 계속 뒤로 밀려 영원히 지워지지 않는다.
     */
    public void delete() {
        if (!this.isDeleted) {
            this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        this.isDeleted = true;
        this.status = ProjectStatus.DELETED;
    }
}