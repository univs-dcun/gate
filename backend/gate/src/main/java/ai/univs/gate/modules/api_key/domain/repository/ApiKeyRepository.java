package ai.univs.gate.modules.api_key.domain.repository;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

    ApiKey save(ApiKey apiKey);

    /**
     * 활성 키 중 <b>가장 최근에 발급된</b> 것 (UG-302).
     *
     * <p>예전에는 {@code findActiveByProjectId} 가 {@code Optional} 을 돌려주는 파생 쿼리였다.
     * 활성 행이 2개면 {@code IncorrectResultSizeDataAccessException} 이 나서 <b>프로젝트 상세
     * 조회와 키 재발급이 둘 다 500</b> 이 됐다. 재발급이 막히면 그 프로젝트는 스스로 빠져나올
     * 방법이 없다 — 상태를 고치는 유일한 수단이 바로 그 재발급이기 때문이다.
     *
     * <p>그래서 "하나뿐" 을 <b>가정</b>하는 대신 어긋난 상태를 <b>견디고 알린다.</b> 2개 이상이면
     * 구현체가 ERROR 로 남기고 가장 최근 것을 돌려준다. 조용히 하나를 고르는 것은 문제를 숨기는
     * 쪽이라 로그를 함께 두는 것이 이 선택의 전제다.
     *
     * <p>어긋난 상태 자체는 {@code RegenerateApiKeyUseCase} 가 프로젝트 행을 잠그고 활성 키를
     * <b>전부</b> 끈 뒤 하나만 새로 넣는 것으로 정리된다. 즉 이 메서드가 ERROR 를 남기는 프로젝트도
     * 재발급 한 번이면 정상으로 돌아온다.
     *
     * <p><b>남은 것.</b> {@code (project_id, is_active)} 부분 유니크 인덱스는 아직 없다. 넣으면
     * 어긋난 상태가 애초에 생기지 않지만 DB 마이그레이션이고, 기존 데이터에 위반 행이 있으면
     * 인덱스 생성 자체가 실패하므로 확인이 먼저다 (UG-302).
     */
    Optional<ApiKey> findLatestActiveByProjectId(Long projectId);

    /**
     * 활성 키를 <b>전부</b> 가져온다 (UG-288).
     *
     * <p>삭제·재발급처럼 "활성인 것을 모두 정리한다" 가 맞는 자리에서 쓴다. 몇 개가 있든 전부
     * 끄는 것이 정리 동작의 의미에 맞고, 어긋난 상태를 그 자리에서 해소한다.
     */
    List<ApiKey> findAllActiveByProjectId(Long projectId);

    Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey);

    boolean existsByApiKey(String apiKey);
}
