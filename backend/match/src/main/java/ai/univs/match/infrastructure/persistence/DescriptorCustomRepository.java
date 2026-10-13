package ai.univs.match.infrastructure.persistence;

import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface DescriptorCustomRepository {

    Double oneToOneMatch(byte[] requestDescriptor, byte[] target, int version);

    MatchResultProjection oneToManyMatch(Long branchId, byte[] requestDescriptor, int version);

    /**
     * 거리가 가까운 순으로 최대 {@code limit} 건 (UG-314).
     *
     * <p>임계치는 <b>받지 않는다.</b> 유사도 변환이 Platt scaling
     * {@code 1 / (1 + exp(A·distance + B))} 이고 지원하는 세 버전 모두 A 가 양수라, 거리가
     * 커지면 유사도는 반드시 작아진다. 즉 <b>거리 상위 k건 = 유사도 상위 k건</b>이고, 그중
     * 임계치 미달을 잘라내면 그게 곧 "임계치를 넘는 최대 k건" 이다 — 더 가져와 채울 대상이
     * 애초에 존재하지 않는다.
     *
     * <p>임계치 판정을 face-service 에 남기는 이유는 경계값 때문이다. 유사도는 소수점 5자리
     * 반올림 후 문자열로 비교되는데, 임계치를 SQL 로 내리려면 역변환으로 거리 컷을 계산해야
     * 하고 그건 <b>반올림 전</b> 값으로 자르는 것이라 기존 1:N 과 판정이 어긋난다.
     *
     * <p>성능상 손해도 없다. 이 쿼리는 브랜치 전 행에 {@code vlmatch()} 를 돌리는 풀스캔이라
     * WHERE 를 넣든 상위 k건만 받든 스캔량이 같다.
     */
    List<MatchResultProjection> oneToManyMatchTopK(
            Long branchId, byte[] requestDescriptor, int version, int limit);

    /**
     * 커밋 뒤 재검사를 브랜치마다 한 번에 하나씩 돌게 브랜치 행을 잠근다 (UG-374). 트랜잭션이 끝나면 풀린다.
     *
     * <p>잠그는 동안 하는 일은 {@link #nearestRecent} 한 번과 (중복이면) 자기 행 삭제뿐이라 짧다. 1:N 중복 검사는 이 잠금
     * 밖에서 돈다. <b>특징점 INSERT 를 막으면 안 된다.</b> 지금 {@code descriptor.branch_id} 에는 외래키가 없어 INSERT 가
     * 브랜치 행을 건드리지 않는다 — 어느 잠금이든 INSERT 는 기다리지 않는다(실제 DB 로 확인). 외래키가 생기면 PostgreSQL 의
     * 자식 INSERT 가 부모 행에 {@code FOR KEY SHARE} 를 걸고, {@code FOR UPDATE} 는 그것과 충돌해 같은 브랜치의 등록이 전부
     * 이 잠금 뒤에 줄을 선다. 그래서 PostgreSQL 은 미리 {@code FOR NO KEY UPDATE} 로 건다(서로끼리는 충돌, KEY SHARE 와는
     * 충돌 안 함). Oracle 은 외래키가 있어도 자식 INSERT 가 부모 행을 잠그지 않아 {@code FOR UPDATE} 로 충분하다.
     *
     * <p><b>대기 상한 {@link #RECHECK_LOCK_WAIT}.</b> 넘으면 던지고, 호출자는 등록을 유지한다(재검사 실패). 상한이 없으면
     * 한 브랜치에 등록이 몰릴 때 대기가 DB 쿼리 상한(PG statement_timeout 3.5초, Oracle 은 소켓 4초)까지 늘어, 1:N 검사와
     * 합쳐 face→match 읽기 상한(5초)을 넘을 수 있다 — 그러면 face·gate 는 실패로 기록하는데 match 는 저장한 채로 남는다.
     */
    void lockBranchForRecheck(Long branchId);

    /** {@link #lockBranchForRecheck} 의 잠금 대기 상한. Oracle {@code WAIT} 가 초 단위라 초로 둔다. */
    Duration RECHECK_LOCK_WAIT = Duration.ofSeconds(1);

    /**
     * 같은 브랜치에서 {@code since} 이후에 저장된 같은 버전 특징점 중 가장 가까운 1건 (UG-374 커밋 뒤 재검사). 자기 자신
     * ({@code excludeDescriptorId})은 뺀다. 없으면 빈 값.
     *
     * <p>{@code (branch_id, created_at)} 인덱스(V6)로 최근 행만 읽는다 — 브랜치 전체를 훑는 {@link #oneToManyMatch} 와
     * 다르다. 버전이 다른 특징점은 비교하지 않는다 — 추출기가 다르면 거리가 같은 척도가 아니다.
     */
    Optional<MatchResultProjection> nearestRecent(Long branchId,
                                                  Long excludeDescriptorId,
                                                  LocalDateTime since,
                                                  int descriptorVersion,
                                                  byte[] requestDescriptor,
                                                  int version);
}
