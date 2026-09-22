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
     * 조회가 500</b> 이 됐다. 데이터가 어긋났다는 이유로 멀쩡한 읽기까지 막는 셈이었다.
     *
     * <p>그래서 "하나뿐" 을 <b>가정</b>하는 대신 어긋난 상태를 <b>견디고 알린다.</b> 2개 이상이면
     * 구현체가 ERROR 로 남기고 가장 최근 것을 돌려준다. 조용히 하나를 고르는 것은 문제를 숨기는
     * 쪽이라 로그를 함께 두는 것이 이 선택의 전제다.
     *
     * <p>어긋난 상태는 V23 마이그레이션이 한 번 정리했고, V24 의 부분 유니크 인덱스가 재발을 막는다.
     * 즉 이 메서드의 ERROR 는 <b>인덱스가 없는 환경</b>(아직 마이그레이션이 안 돈 곳)에서만 볼 수
     * 있어야 한다. 보인다면 그 자체가 조사 신호다.
     *
     * <p>UG-312 에서 키 재발급 기능이 제거되면서 활성 키를 만드는 경로는 프로젝트 생성 한 곳뿐이
     * 됐다 — 프로젝트 1개 = API 키 1개다. {@code SingleActiveApiKeyGuardTest} 가 그것을 지킨다.
     */
    Optional<ApiKey> findLatestActiveByProjectId(Long projectId);

    /**
     * 활성 키를 <b>전부</b> 가져온다 (UG-288).
     *
     * <p>프로젝트 삭제처럼 "활성인 것을 모두 정리한다" 가 맞는 자리에서 쓴다. 몇 개가 있든 전부
     * 끄는 것이 정리 동작의 의미에 맞고, 어긋난 상태를 그 자리에서 해소한다.
     */
    List<ApiKey> findAllActiveByProjectId(Long projectId);

    /**
     * 활성 키를 찾는다. <b>프로젝트가 삭제됐는지는 보지 않는다.</b>
     *
     * <p>UG-300 이후 인증·데모 경로는 {@link #findActiveByApiKeyWithLiveProject} 를 쓴다. 이
     * 메서드는 그 조회가 비었을 때 "키 자체가 없는 것" 과 "프로젝트가 삭제된 것" 을 구분해
     * 로그로 남기는 <b>진단용</b>으로만 남는다. 응답은 어느 쪽이든 같다 (열거 오라클 방지).
     */
    Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey);

    /**
     * 활성 키 중 <b>살아 있는 프로젝트</b>의 것만 (UG-300).
     *
     * <p>"삭제된 프로젝트의 키를 거부한다"(UG-288)를 조회 조건으로 강제한다. 자바 조건은 새
     * 조회 경로가 생기면 조용히 뚫리지만, 조회 조건은 그 쿼리를 쓰는 모든 호출처에 자동으로
     * 붙는다.
     */
    Optional<ApiKey> findActiveByApiKeyWithLiveProject(String apiKey);

    boolean existsByApiKey(String apiKey);
}
