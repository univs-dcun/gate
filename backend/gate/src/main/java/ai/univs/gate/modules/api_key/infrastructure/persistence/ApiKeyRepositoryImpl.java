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
     * <p>여기서 예외를 던지면 그 프로젝트는 상세 조회조차 못 하게 된다 — 데이터가 어긋났다는
     * 이유로 멀쩡한 읽기까지 막는 셈이다. 그래서 견디고 알린다.
     *
     * <p>ERROR 레벨인 이유는 이것이 데이터 정합성 문제이지 운영 중 흔히 나는 상황이 아니기
     * 때문이다. <b>V24 부분 유니크 인덱스가 있는 환경에서는 애초에 생길 수 없다.</b> 그런데도
     * 보인다면 마이그레이션이 안 돈 환경이거나 인덱스가 사라진 것이므로, 로그가 아니라 스키마를
     * 먼저 확인할 것 — 정리는 V23 과 같은 방식(프로젝트별 최신 하나만 남기고 비활성화)으로 한다.
     */
    @Override
    public Optional<ApiKey> findLatestActiveByProjectId(Long projectId) {
        List<ApiKey> active =
                apiKeyJpaRepository.findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
                        projectId, true);

        if (active.size() > 1) {
            log.error("활성 API 키가 {}개다 — V24 부분 유니크 인덱스가 있으면 생길 수 없는 "
                            + "상태다. 스키마를 확인할 것. 가장 최근 것을 쓴다. "
                            + "projectId={}, apiKeyIds={}",
                    active.size(), projectId, active.stream().map(ApiKey::getId).toList());
        }

        return active.stream().findFirst();
    }

    @Override
    public List<ApiKey> findAllActiveByProjectId(Long projectId) {
        return apiKeyJpaRepository.findAllByProjectIdAndIsActive(projectId, true);
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
