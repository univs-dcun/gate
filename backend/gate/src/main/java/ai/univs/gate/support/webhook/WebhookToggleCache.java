package ai.univs.gate.support.webhook;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.shared.web.enums.CallerType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 프로젝트별 웹훅 토글(API·데모)을 잠깐 기억한다 (UG-361).
 *
 * <p>UG-361 이전에는 웹훅이 없는 프로젝트도 요청마다 전송 대기열에 들어가, 처리량이 오르면 대기열이 차서 웹훅을 켠
 * 프로젝트의 이벤트까지 버려졌다(scaling 재측정 B, 드롭 WARN 약 17.5만 건). 대기열에 넣기 전에 여기서 「이 경로는
 * 꺼져 있다」를 알면 넣지 않는다.
 *
 * <p><b>요청 스레드는 DB 를 읽지 않는다.</b> 값은 전송 스레드가 설정을 읽을 때 채운다. 모르면 예전처럼 대기열에 넣고,
 * 전송 스레드가 DB 로 확인한다. 요청 경로에 커넥션 사용을 늘리지 않으려는 것이다 — gate 처리량의 상한이 커넥션
 * 풀이다(UG-359).
 *
 * <p><b>오래된 값은 한 건만 확인하러 보낸다.</b> {@link #FRESH} 가 지난 「꺼짐」을 바로 잊으면, 대기열이 밀려 있을 때
 * 다시 채워질 때까지 그 프로젝트의 요청이 전부 대기열로 몰려 이 가드가 정작 혼잡할 때 풀린다(반박 리뷰 M-1). 그래서
 * 프로젝트당 확인 한 건({@link Decision#PROBE})만 넣고 나머지는 그동안 계속 건너뛴다. 확인이 {@link #MAX_AGE} 안에
 * 돌아오지 않으면 그때 잊는다.
 *
 * <p><b>틀려도 되는 쪽은 「켜짐」뿐이다.</b> 켜짐으로 기억했는데 꺼졌다면 전송 스레드가 DB 를 다시 보고 버린다.
 * 꺼짐으로 기억했는데 켜졌다면 그동안의 이벤트를 놓친다. 그래서 설정을 바꾸는 쪽이 커밋 뒤에 {@link #evictAfterCommit}
 * 으로 지운다. 다른 gate 인스턴스는 지우지 못하므로 그 인스턴스에서는 {@link #FRESH} 에 확인 시간을 더한 만큼(최대
 * {@link #MAX_AGE}) 늦게 반영된다.
 */
@Component
public class WebhookToggleCache {

    /** 이 안의 값은 확인 없이 믿는다. */
    static final Duration FRESH = Duration.ofSeconds(10);
    /** 확인이 돌아오지 않아도 이보다 오래된 값은 잊는다. 다른 인스턴스에서 바꾼 설정이 늦게 반영되는 최대 시간. */
    static final Duration MAX_AGE = Duration.ofSeconds(30);
    private static final long MAX_PROJECTS = 10_000;

    /** 대기열에 넣을지. */
    enum Decision {
        /** 넣는다 — 켜져 있거나 모른다. */
        ENQUEUE,
        /** 넣는다 — 오래된 「꺼짐」을 확인하러 가는 한 건이다. 넣지 못하면 {@link #probeDropped} 를 부른다. */
        PROBE,
        /** 넣지 않는다 — 꺼져 있다. */
        SKIP
    }

    /** 테스트가 시간을 앞당기려고 바꾼다. */
    LongSupplier nanoTime = System::nanoTime;

    private final Cache<Long, Entry> cache = Caffeine.newBuilder()
            .expireAfterWrite(MAX_AGE)
            .maximumSize(MAX_PROJECTS)
            .ticker(() -> nanoTime.getAsLong())
            .build();

    /** 지울 때마다 오른다. 읽는 사이에 바뀐 설정을 낡은 값으로 다시 채우지 않으려고 쓴다 ({@link #remember}). */
    private final AtomicLong generation = new AtomicLong();

    Decision decide(Long projectId, CallerType source) {
        if (projectId == null) return Decision.ENQUEUE;   // 전송 스레드가 예전처럼 처리하게 둔다
        Entry entry = cache.getIfPresent(projectId);
        if (entry == null || entry.unknown || entry.enabled(source)) return Decision.ENQUEUE;
        if (nanoTime.getAsLong() - entry.writtenAt < FRESH.toNanos()) return Decision.SKIP;
        return entry.probing.compareAndSet(false, true) ? Decision.PROBE : Decision.SKIP;
    }

    /** 확인하러 보낸 건이 대기열에 들어가지 못했다 — 다음 요청이 다시 확인하게 한다. */
    void probeDropped(Long projectId) {
        Entry entry = cache.getIfPresent(projectId);
        if (entry != null) entry.probing.set(false);
    }

    /** 설정을 읽기 <b>전에</b> 받아 두고 {@link #remember} 에 넘긴다. */
    long generation() {
        return generation.get();
    }

    /**
     * 전송 스레드가 DB 에서 읽은 설정을 기억한다. 읽기 시작한 뒤 누가 지웠거나 더 새로 읽은 값이 있으면 덮지 않는다.
     *
     * <p>프로젝트 단위로 원자적으로 비교하고 넣는다 — 지우는 쪽은 「모름」 자리표를 새 세대로 넣으므로, 낡은 값이 그 뒤에
     * 끼어들어 잠깐이라도 보이는 틈이 없다(반박 리뷰 L-2).
     *
     * @param config 없으면 {@code null} — 웹훅이 없는 프로젝트다
     */
    void remember(Long projectId, WebhookConfig config, long readGeneration) {
        if (projectId == null) return;
        Entry read = Entry.of(config, readGeneration, nanoTime.getAsLong());
        cache.asMap().compute(projectId, (id, current) ->
                current != null && current.generation > readGeneration ? current : read);
    }

    /** 설정을 바꾼 트랜잭션이 커밋된 뒤에 지운다. 트랜잭션 밖이면 바로 지운다. */
    public void evictAfterCommit(Long projectId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(projectId);
            return;
        }
        // 커밋 전에 지우면 전송 스레드가 아직 커밋 안 된 옛 값을 읽어 다시 채운다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(projectId);
            }
        });
    }

    void evict(Long projectId) {
        long next = generation.incrementAndGet();
        cache.put(projectId, Entry.unknown(next, nanoTime.getAsLong()));
    }

    static boolean isEnabled(WebhookConfig config, CallerType source) {
        return Entry.of(config, 0, 0).enabled(source);
    }

    private static final class Entry {

        final boolean api;
        final boolean demo;
        /** 「모름」 자리표 — 지운 뒤 이보다 낡은 값이 다시 들어오지 못하게 막는다. */
        final boolean unknown;
        final long generation;
        final long writtenAt;
        final AtomicBoolean probing = new AtomicBoolean();

        private Entry(boolean api, boolean demo, boolean unknown, long generation, long writtenAt) {
            this.api = api;
            this.demo = demo;
            this.unknown = unknown;
            this.generation = generation;
            this.writtenAt = writtenAt;
        }

        static Entry of(WebhookConfig config, long generation, long writtenAt) {
            if (config == null) return new Entry(false, false, false, generation, writtenAt);
            return new Entry(Boolean.TRUE.equals(config.getApiEnabled()), Boolean.TRUE.equals(config.getDemoEnabled()),
                    false, generation, writtenAt);
        }

        static Entry unknown(long generation, long writtenAt) {
            return new Entry(false, false, true, generation, writtenAt);
        }

        boolean enabled(CallerType source) {
            return switch (source) {
                case API  -> api;
                case DEMO -> demo;
            };
        }
    }
}
