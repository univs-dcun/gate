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

    /**
     * 지금까지의 변경을 DB 에 즉시 내보낸다 (UG-302 V24).
     *
     * <p>영속성 계층 관심사를 도메인 계약에 두는 것은 원래 피해야 하지만, 여기서는 <b>쓰기
     * 순서 자체가 지켜야 할 불변식</b>이라 명시적으로 노출한다.
     *
     * <p>V24 가 만드는 부분 유니크 인덱스는 "프로젝트당 활성 키 하나" 를 DB 가 강제한다.
     * 그런데 재발급은 "기존 키 비활성화 → 새 키 삽입" 이고, 비활성화는 더티 마킹일 뿐이라
     * 그대로 두면 UPDATE 가 INSERT 뒤로 밀린다 — {@code @GeneratedValue(IDENTITY)} 라
     * {@code save()} 가 id 를 받으려고 INSERT 를 즉시 내보내는데, 그 시점에 DB 에는 기존 행이
     * 아직 활성이다. 인덱스가 그 순간을 잡아 <b>재발급 전체가 실패</b>한다.
     *
     * <p>반박 리뷰가 실측으로 찾았다. 자가 치유 수단인 재발급이 죽으면 UG-302 가 만든 복구
     * 경로까지 함께 사라진다 — 인덱스가 고치려던 문제를 인덱스가 되살리는 셈이었다.
     *
     * <p>지연 제약(deferrable)으로는 못 피한다. 부분 인덱스는 제약으로 선언할 수 없어
     * postgresql·오라클 모두 지연시킬 수 없다.
     */
    void flush();

    Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey);

    boolean existsByApiKey(String apiKey);
}
