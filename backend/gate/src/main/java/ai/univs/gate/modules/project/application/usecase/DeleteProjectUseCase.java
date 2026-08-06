package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.support.project.ProjectService;
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
     * <p>활성 키를 하나로 보는 것은 이 코드베이스 전체의 전제다 — {@code findActiveByProjectId} 가
     * {@code Optional} 을 돌려주고, {@code RegenerateApiKeyUseCase} 도 기존 키 하나를 끄고 새로
     * 하나를 발급한다.
     */
    @Transactional
    public void execute(Long accountId, Long projectId) {
        Project project = projectService.validateOwnership(projectId, accountId);
        project.delete();

        apiKeyRepository.findActiveByProjectId(projectId).ifPresent(apiKey -> {
            apiKey.deactivate();
            log.info("프로젝트 삭제로 API 키를 비활성화했다. projectId={}, apiKeyId={}",
                    projectId, apiKey.getId());
        });
    }
}
