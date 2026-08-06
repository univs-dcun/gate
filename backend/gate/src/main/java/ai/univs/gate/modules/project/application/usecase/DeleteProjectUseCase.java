package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.project.ProjectService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectService projectService;
    private final ApiKeyRepository apiKeyRepository;

    /**
     * 프로젝트를 삭제하고 그 프로젝트의 API 키를 함께 비활성화한다 (UG-288).
     *
     * <p>키 비활성화가 없으면 삭제된 프로젝트의 키로 특징점 등록·매칭·이력 조회가 계속 된다.
     * {@code ApiKeyService} 의 조회 진입점에도 삭제 검사를 넣었으므로 방어는 두 겹이다. 여기서
     * 한 번 더 끄는 이유는 <b>의미</b>다 — 삭제된 프로젝트의 키가 DB 에 {@code is_active = true} 로
     * 남아 있으면, 그 행을 보는 사람도 다른 조회 경로도 그것을 살아 있는 키로 읽는다.
     *
     * <p><b>활성 키가 하나라고 가정하지 않는다</b> (반박 리뷰 지적). 코드베이스 곳곳이 하나를
     * 전제하지만({@code findActiveByProjectId} 가 {@code Optional}), 그것을 보장하는 제약이
     * 스키마에 없다. 여기서 {@code Optional} 조회를 쓰면 활성 키가 2개인 프로젝트는
     * {@code IncorrectResultSizeDataAccessException} 으로 삭제가 롤백돼 <b>영영 지울 수 없게</b>
     * 된다 — 고칠 수단이 사라지는 셈이다. 삭제는 정리 동작이므로 몇 개가 있든 전부 끈다.
     *
     * <p>{@code @Transactional} 은 이 메서드의 전부다. 떼거나 {@code readOnly = true} 로 바꾸면
     * 더티 체킹이 flush 되지 않아 <b>UG-288 과 똑같이 조용히 아무 일도 하지 않게</b> 된다.
     * {@code TransactionDeclarationGuardTest} 가 그것을 막는다.
     */
    @Transactional
    public void execute(Long accountId, Long projectId) {
        Project project = projectService.validateOwnership(projectId, accountId);
        project.delete();

        List<ApiKey> activeKeys = apiKeyRepository.findAllActiveByProjectId(projectId);
        activeKeys.forEach(ApiKey::deactivate);

        if (!activeKeys.isEmpty()) {
            log.info("프로젝트 삭제로 API 키를 비활성화했다. projectId={}, apiKeyIds={}",
                    projectId, activeKeys.stream().map(ApiKey::getId).toList());
        }
    }
}
