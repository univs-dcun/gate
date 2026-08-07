package ai.univs.gate.modules.api_key.infrastructure.persistence;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiKeyRepositoryImpl implements ApiKeyRepository {

    private final ApiKeyJpaRepository apiKeyJpaRepository;

    @Override
    public ApiKey save(ApiKey apiKey) {
        return apiKeyJpaRepository.save(apiKey);
    }

    /**
     * 활성 키가 2개 이상이면 ERROR 를 남기고 가장 최근 것을 돌려준다 (UG-302).
     *
     * <p>여기서 예외를 던지면 프로젝트 상세 조회와 키 재발급이 둘 다 500 이 되고, 재발급이
     * 막히는 순간 그 프로젝트는 <b>스스로 고칠 수단을 잃는다.</b> 그래서 견디고 알린다.
     *
     * <p>ERROR 레벨인 이유는 이것이 스키마 제약 부재로 생긴 데이터 정합성 문제이지 운영 중
     * 흔히 나는 상황이 아니기 때문이다. 한 건이라도 보이면 그 프로젝트에 재발급을 한 번
     * 돌려 정리해야 한다.
     */
    @Override
    public Optional<ApiKey> findLatestActiveByProjectId(Long projectId) {
        List<ApiKey> active =
                apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                        projectId, true);

        if (active.size() > 1) {
            log.error("활성 API 키가 {}개다 — (project_id, is_active) 제약이 없어 생길 수 있는 "
                            + "상태다. 가장 최근 것을 쓴다. projectId={}, apiKeyIds={}",
                    active.size(), projectId, active.stream().map(ApiKey::getId).toList());
        }

        return active.stream().findFirst();
    }

    @Override
    public List<ApiKey> findAllActiveByProjectId(Long projectId) {
        return apiKeyJpaRepository.findAllByProjectIdAndIsActive(projectId, true);
    }

    @Override
    public void flush() {
        apiKeyJpaRepository.flush();
    }

    @Override
    public Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey) {
        return apiKeyJpaRepository.findByApiKeyAndIsActive(apiKey, true);
    }

    @Override
    public boolean existsByApiKey(String apiKey) {
        return apiKeyJpaRepository.existsByApiKey(apiKey);
    }
}
