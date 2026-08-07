package ai.univs.gate.support.project;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public Project validateOwnership(Long projectId, Long userId) {
        return validateOwnership(projectId, userId, projectRepository::findByIdAndIsDeletedFalse);
    }

    /**
     * 소유를 검증하면서 프로젝트 행에 <b>쓰기 잠금</b>을 건다 (UG-302).
     *
     * <p>같은 프로젝트를 고치는 요청끼리 직렬화해야 하는 자리에서 쓴다. 잠금은 <b>경쟁하는 모든
     * 쓰기 경로가 같이 잡아야</b> 의미가 있다 — 한쪽만 잠그면 직렬화가 되지 않는다.
     *
     * <p>UG-302 때는 키 재발급과 프로젝트 삭제 두 곳이었다. UG-312 에서 재발급이 제거돼 지금은
     * 삭제뿐이다. {@code UpdateProjectUseCase} 는 아직 잠그지 않는데, 그게 UG-311 이다 —
     * 삭제가 커밋된 뒤 먼저 시작된 수정이 더티 체킹으로 {@code is_deleted} 를 되써 프로젝트를
     * 부활시킨다.
     *
     * <p>읽기 전용 경로에는 쓰지 않는다. 상세 조회까지 잠그면 재발급이 도는 동안 조회가 막힌다.
     *
     * <p>잠금을 잡기 <b>전에</b> 삭제 여부를 함께 거르므로 (쿼리의 {@code IsDeletedFalse}),
     * 삭제가 먼저 커밋되면 뒤에 온 재발급은 잠금을 얻은 뒤 행을 찾지 못해
     * {@code PROJECT_NOT_FOUND} 로 끝난다 — 삭제된 프로젝트에 활성 키가 다시 생기는 일이 없다.
     */
    public Project validateOwnershipForUpdate(Long projectId, Long userId) {
        return validateOwnership(
                projectId, userId, projectRepository::findForUpdateByIdAndIsDeletedFalse);
    }

    private Project validateOwnership(
            Long projectId, Long userId, Function<Long, Optional<Project>> lookup) {
        Project project = lookup.apply(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.PROJECT_NOT_FOUND));

        validateOwnership(project, userId);

        return project;
    }

    private void validateOwnership(Project project, Long userId) {
        if (!project.getAccountId().equals(userId)) {
            throw new CustomGateException(ErrorType.NOT_OWNERSHIP);
        }
    }
}
