package ai.univs.gate.modules.api_key.domain.repository;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

    ApiKey save(ApiKey apiKey);

    Optional<ApiKey> findActiveByProjectId(Long projectId);

    /**
     * 활성 키를 <b>전부</b> 가져온다 (UG-288).
     *
     * <p>{@link #findActiveByProjectId} 는 {@code Optional} 이라 활성 행이 2개면
     * {@code IncorrectResultSizeDataAccessException} 을 던진다. 그런 상태를 막는 제약이 스키마에
     * 없다 — {@code api_keys} 에는 {@code UNIQUE (api_key)} 뿐이고
     * {@code (project_id, is_active)} 부분 유니크 인덱스가 없으며, {@code RegenerateApiKeyUseCase}
     * 는 잠금 없이 "기존 비활성화 → 새 키 삽입" 을 하므로 동시 호출이면 활성 2개가 남을 수 있다.
     *
     * <p>삭제 경로에서 그 예외가 나면 같은 트랜잭션의 프로젝트 삭제까지 롤백된다. 즉 키가
     * 어긋난 프로젝트는 <b>영영 지울 수 없게</b> 된다. 삭제는 정리 동작이므로 몇 개가 있든
     * 전부 끄는 것이 맞다.
     */
    List<ApiKey> findAllActiveByProjectId(Long projectId);

    Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey);

    boolean existsByApiKey(String apiKey);
}
