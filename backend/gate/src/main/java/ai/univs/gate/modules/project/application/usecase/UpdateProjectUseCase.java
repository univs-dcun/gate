package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.UpdateProjectInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateProjectUseCase {

    private final ProjectService projectService;

    /**
     * UG-311: 잠그고 읽는다. {@code Project} 는 낙관적 잠금(버전 컬럼)이 없고 더티 체킹 UPDATE 가 전 컬럼을 쓴다
     * (동적 UPDATE 애노테이션은 UG-297 가드가 금지한다) — 잠금 없이 읽으면, 이 트랜잭션이 시작된 뒤 삭제가 먼저 커밋돼도 여기서 읽어 둔
     * {@code is_deleted=false}·{@code status=ACTIVE} 를 그대로 되써 삭제된 프로젝트가 조용히 살아난다
     * (UG-302 리뷰가 H2 2스레드로 재현). 삭제({@code DeleteProjectUseCase})와 같은 쓰기 잠금을 잡으면
     * 순서가 어느 쪽이든 안전하다 — 수정이 먼저 잠그면 삭제가 뒤에 적용되고, 삭제가 먼저 커밋되면
     * {@code IsDeletedFalse} 조건에서 행을 찾지 못해 {@code PROJECT_NOT_FOUND} 로 끝난다.
     */
    @Transactional
    public ProjectResult execute(UpdateProjectInput input) {
        Project project = projectService.validateOwnershipForUpdate(input.projectId(), input.accountId());

        project.updateInfo(input.projectName(), input.description(), input.colorTag());

        return ProjectResult.from(project);
    }
}
