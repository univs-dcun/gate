package ai.univs.gate.support.api_key;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * API 키 조회 결과를 잠깐 기억한다 (UG-364, scaling P2-2).
 *
 * <p>gate 는 요청마다 API 키로 프로젝트를 찾았다(쿼리 2번 + 트랜잭션 하나). 처리량 상한이 gate 커넥션 풀이라(UG-359)
 * 가장 반복되고 거의 바뀌지 않는 이 조회를 캐시에서 답한다. 적중하면 DB·커넥션을 쓰지 않는다.
 *
 * <p><b>보안 규칙(UG-288)을 늦추지 않는다.</b> 키를 무효로 만드는 경로는 프로젝트 삭제 하나뿐이고(키 재발급은 UG-312 에서
 * 제거, 키는 유효 → 무효 한 방향으로만 바뀐다), 그 트랜잭션이 커밋되면 {@link #evictProjectAfterCommit} 이 그 프로젝트의
 * 키를 지운다 — 같은 인스턴스에서는 지금처럼 즉시 거부된다. 다른 gate 인스턴스는 지우지 못하므로 {@link #TTL} 이 상한이다.
 * 지금은 환경마다 gate 가 한 대다.
 *
 * <ul>
 *   <li>캐시 키는 API 키 문자열 그대로다. 다른 키로는 적중하지 않는다.
 *   <li>소유 검증(요청 계정 = 프로젝트 소유자)은 적중해도 요청마다 한다 — 캐시는 조회 결과만 담는다.
 *   <li>없는 키는 기억하지 않는다. 새 키는 바로 쓸 수 있고, 거부 응답은 예전처럼 DB 로 확인한다.
 * </ul>
 *
 * <p><b>지운 뒤 낡은 값이 다시 들어오지 않게 한다.</b> 삭제가 커밋되기 직전에 읽은 요청이 지운 뒤에 넣으면 삭제된 프로젝트의
 * 키가 {@link #TTL} 동안 통과한다. 그래서 지울 때 프로젝트별 「지운 세대」를 남기고, 넣을 때 읽기 전에 받아 둔 세대와 비교해
 * 그보다 앞서 읽은 값은 넣지 않는다. 꺼낼 때도 같은 비교를 한다 — 넣기와 지우기가 겹쳐 지우기가 놓친 값이 남아 있어도
 * 쓰이지 않는다.
 *
 * <p><b>전제: DB 읽기는 「지운 세대」가 남는 시간(TTL 의 두 배, 60초)보다 짧다.</b> gate-config 의 statement_timeout(8초)·
 * socketTimeout(10초)이 이를 보장한다(UG-367). 그보다 오래 걸린 읽기는 세대 기록이 사라진 뒤에 넣어 낡은 값이 남을 수 있으니
 * 상한을 늘릴 때 함께 본다.
 */
@Component
public class ApiKeyLookupCache {

    /** 다른 인스턴스에서 삭제한 프로젝트의 키가 이 인스턴스에서 통과할 수 있는 최대 시간. */
    static final Duration TTL = Duration.ofSeconds(30);
    private static final long MAX_KEYS = 10_000;

    /** 테스트가 시간을 앞당기려고 바꾼다. */
    LongSupplier nanoTime = System::nanoTime;

    private final Cache<String, Stamped> entries = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_KEYS)
            .ticker(() -> nanoTime.getAsLong())
            .build();

    /** 프로젝트별로 마지막으로 지운 세대. 넣는 쪽이 낡은 값인지 가린다 — 경합하는 읽기보다 오래 남으면 된다. */
    private final Cache<Long, Long> evictedAt = Caffeine.newBuilder()
            .expireAfterWrite(TTL.multipliedBy(2))
            .ticker(() -> nanoTime.getAsLong())
            .build();

    private final AtomicLong generation = new AtomicLong();

    /** 조회하기 <b>전에</b> 받아 두고 {@link #put} 에 넘긴다. */
    long generation() {
        return generation.get();
    }

    ApiKeySnapshot get(String apiKey) {
        if (apiKey == null) return null;
        Stamped stamped = entries.getIfPresent(apiKey);
        if (stamped == null) return null;
        if (isStale(stamped)) {
            entries.asMap().remove(apiKey, stamped);
            return null;
        }
        return stamped.snapshot();
    }

    /** 조회한 값을 넣는다. 읽기 시작한 뒤 그 프로젝트가 지워졌으면 넣지 않는다. */
    void put(String apiKey, ApiKeySnapshot snapshot, long readGeneration) {
        if (apiKey == null) return;
        Stamped read = new Stamped(snapshot, readGeneration);
        entries.asMap().compute(apiKey, (key, current) -> isStale(read) ? current : read);
    }

    /**
     * 프로젝트를 바꾼(삭제·수정) 트랜잭션이 끝난 뒤에 그 프로젝트의 키를 지운다. 트랜잭션 밖이면 바로 지운다.
     *
     * <p>커밋만이 아니라 <b>끝나기만 하면 결과와 상관없이</b> 지운다(반박 리뷰 M1). DB 가 멈춘 사이 COMMIT 이 서버에서는 성공했는데
     * 드라이버가 타임아웃을 받으면 Spring 은 실패로 보고 {@code afterCommit} 을 부르지 않는다({@code STATUS_UNKNOWN}) — 그러면
     * 삭제된 프로젝트의 키가 캐시 수명 동안 통과한다. 롤백 때 지워도 해는 없다(캐시 적중이 한 번 줄 뿐).
     */
    public void evictProjectAfterCommit(Long projectId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictProject(projectId);
            return;
        }
        // 끝나기 전에 지우면 다른 요청이 아직 커밋 안 된 옛 행을 읽어 다시 채운다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                evictProject(projectId);
            }
        });
    }

    void evictProject(Long projectId) {
        // 세대를 먼저 남긴 뒤 지운다 — 이 사이에 넣으려는 낡은 값은 세대 비교에 걸리고, 그 전에 들어간 값은 아래에서 지워진다.
        evictedAt.put(projectId, generation.incrementAndGet());
        entries.asMap().values().removeIf(stamped -> projectId.equals(stamped.snapshot().projectId()));
    }

    /** 이 값을 읽기 시작한 뒤 그 프로젝트가 지워졌는가. */
    private boolean isStale(Stamped stamped) {
        Long evicted = evictedAt.getIfPresent(stamped.snapshot().projectId());
        return evicted != null && evicted > stamped.readGeneration();
    }

    /**
     * 전부 비운다. 같은 키 문자열로 행을 지웠다 다시 만드는 테스트가 테스트 사이에 부른다.
     *
     * <p><b>운영 코드에서 쓰지 않는다.</b> 프로젝트별 「지운 세대」를 남기지 않으므로, 동시에 진행 중인 읽기가 낡은 값을 다시 넣을 수
     * 있다 — 무효화가 필요하면 {@link #evictProjectAfterCommit} 을 쓴다.
     */
    public void invalidateAll() {
        generation.incrementAndGet();
        entries.invalidateAll();
    }

    /** @param readGeneration 이 값을 DB 에서 읽기 전의 세대 */
    private record Stamped(ApiKeySnapshot snapshot, long readGeneration) {
    }
}
