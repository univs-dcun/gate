package ai.univs.gate.modules.api_key.infrastructure.persistence;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKey, Long> {

    ApiKey save(ApiKey apiKey);

    /**
     * 활성 키 목록을 최신순으로. UG-302 이전에는 {@code Optional} 파생 쿼리였는데, 활성 행이
     * 2개면 스프링 데이터가 {@code IncorrectResultSizeDataAccessException} 을 던져 조회 자체가
     * 500 이 됐다. 목록으로 받아 구현체가 판단한다.
     *
     * <p>{@code issuedAt} 만으로는 같은 초에 발급된 두 키의 순서가 정해지지 않는다. 그러면
     * "가장 최근" 이 호출마다 달라질 수 있어 {@code id} 를 2차 정렬 키로 둔다.
     */
    List<ApiKey> findAllByProjectIdAndIsActiveOrderByIssuedAtDescIdDesc(
            Long projectId, boolean isActive);

    List<ApiKey> findAllByProjectIdAndIsActive(Long projectId, boolean isActive);

    Optional<ApiKey> findByApiKeyAndIsActive(String apiKey, boolean isActive);

    /**
     * 활성 키 중 <b>살아 있는 프로젝트</b>의 것만 (UG-300, UG-288 후속).
     *
     * <p>삭제된 프로젝트의 키를 거부하는 규칙이 자바 조건에서 여기로 내려왔다. 조회 조건에
     * 얹힌 보안 규칙은 호출처가 늘어도 빠질 수 없다 — 자바 조건은 새 조회 메서드를 하나
     * 만들면 조용히 뚫린다.
     *
     * <p>원래 UG-288 은 이 자리를 골랐다가 되돌렸다. 검증할 슬라이스 테스트가 없어서였다
     * ({@code JpaSliceTest} 참고). UG-300 이 그 인프라를 만들었으므로 제자리로 옮긴다.
     */
    Optional<ApiKey> findByApiKeyAndIsActiveAndProject_IsDeletedFalse(
            String apiKey, boolean isActive);

    boolean existsByApiKey(String apiKey);
}
