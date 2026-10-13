package ai.univs.match.infrastructure.persistence;

import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * 브랜치를 고유 제약 위반 없이 만든다 (UG-374, UG-372 후속).
 *
 * <p>UG-372 는 같은 이름을 동시에 만들다 진 요청이 {@code uk_branch_branch_name} 위반을 받고 다시 읽게 했다. 요청은 성공하지만
 * 위반마다 Hibernate {@code SqlExceptionHelper} 가 ERROR 를 한 줄 남겼다(scaling 134 동시 16 에서 프로젝트당 약 8줄). 이제
 * INSERT 자체가 이미 있으면 아무것도 하지 않는다. Hibernate 의 SQL 오류 로그 등급을 낮추지 않는 이유는 다른 진짜 SQL 오류까지
 * 함께 묻히기 때문이다.
 *
 * <p>{@code @Repository} 는 빼면 안 된다 — 이 표시가 있어야 구현의 JPA 예외가 Spring 의
 * {@link org.springframework.dao.DataIntegrityViolationException} 으로 번역된다. 번역되지 않으면 Oracle 의 드문 위반이
 * {@code BranchProvisioner} 의 "위반을 잡아 다시 읽기" 를 비켜 가 등록 실패가 된다.
 */
@Repository
public interface BranchCustomRepository {

    /**
     * 이 이름의 브랜치가 없으면 넣는다. 넣었으면 1, 이미 있었으면 0.
     *
     * <p>두 DB 모두 위반이 나지 않는다 — 같은 이름이 아직 커밋되지 않았으면 그 트랜잭션이 끝나길 기다렸다가, 커밋됐으면 0 을
     * 돌려준다. PostgreSQL 은 {@code ON CONFLICT DO NOTHING}, Oracle 은 {@code IGNORE_ROW_ON_DUPKEY_INDEX} 힌트. 그래도 호출자는
     * 위반을 잡아 다시 읽는 경로를 남겨 둔다({@code BranchProvisioner}) — 힌트가 무시되는 환경 같은 예외에 대비해서다.
     */
    int insertIfAbsent(String branchName, LocalDateTime now);
}
