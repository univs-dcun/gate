package ai.univs.gate.support.webhook;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.shared.web.enums.CallerType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
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
 * <p><b>요청 스레드는 DB 를 읽지 않는다.</b> 값은 전송 스레드가 설정을 읽을 때 채운다. 모르면(처음이거나 만료) 예전처럼
 * 대기열에 넣고, 전송 스레드가 DB 로 확인한다. 요청 경로에 커넥션 사용을 늘리지 않으려는 것이다 — gate 처리량의
 * 상한이 커넥션 풀이다(UG-359).
 *
 * <p><b>틀려도 되는 쪽은 「켜짐」뿐이다.</b> 켜짐으로 기억했는데 꺼졌다면 전송 스레드가 DB 를 다시 보고 버린다.
 * 꺼짐으로 기억했는데 켜졌다면 그동안의 이벤트를 놓친다. 그래서 설정을 바꾸는 쪽이 커밋 뒤에 {@link #evictAfterCommit}
 * 으로 지운다. 다른 gate 인스턴스는 지우지 못하므로 그 인스턴스에서는 최대 {@link #TTL} 동안 놓칠 수 있다.
 */
@Component
public class WebhookToggleCache {

    /** 다른 인스턴스에서 바꾼 설정이 늦게 반영되는 최대 시간. */
    static final Duration TTL = Duration.ofSeconds(10);
    private static final long MAX_PROJECTS = 10_000;

    /** 테스트가 시간을 앞당기려고 바꾼다. */
    LongSupplier nanoTime = System::nanoTime;

    private final Cache<Long, Toggles> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_PROJECTS)
            .ticker(() -> nanoTime.getAsLong())
            .build();

    /** 지울 때마다 오른다. 읽는 사이에 바뀐 설정을 낡은 값으로 다시 채우지 않으려고 쓴다 ({@link #remember}). */
    private final AtomicLong generation = new AtomicLong();

    /** 이 경로가 꺼져 있다고 확실히 아는가. 모르면 {@code false} — 대기열에 넣어 전송 스레드가 확인하게 한다. */
    boolean knownDisabled(Long projectId, CallerType source) {
        Toggles toggles = cache.getIfPresent(projectId);
        return toggles != null && !toggles.enabled(source);
    }

    /** 설정을 읽기 <b>전에</b> 받아 두고 {@link #remember} 에 넘긴다. */
    long generation() {
        return generation.get();
    }

    /**
     * 전송 스레드가 DB 에서 읽은 설정을 기억한다. 읽기 시작한 뒤 누가 지웠다면 그 값은 낡았을 수 있으니 남기지 않는다.
     *
     * <p>넣은 <b>뒤에</b> 세대를 본다. 지우는 쪽은 세대를 먼저 올리고 지우므로, 어느 순서로 겹쳐도 낡은 값이 남지 않는다.
     *
     * @param config 없으면 {@code null} — 웹훅이 없는 프로젝트다
     */
    void remember(Long projectId, WebhookConfig config, long readGeneration) {
        cache.put(projectId, config == null ? Toggles.NONE : Toggles.of(config));
        if (generation.get() != readGeneration) cache.invalidate(projectId);
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
        generation.incrementAndGet();
        cache.invalidate(projectId);
    }

    static boolean isEnabled(WebhookConfig config, CallerType source) {
        return Toggles.of(config).enabled(source);
    }

    private record Toggles(boolean api, boolean demo) {

        static final Toggles NONE = new Toggles(false, false);

        static Toggles of(WebhookConfig config) {
            return new Toggles(Boolean.TRUE.equals(config.getApiEnabled()), Boolean.TRUE.equals(config.getDemoEnabled()));
        }

        boolean enabled(CallerType source) {
            return switch (source) {
                case API  -> api;
                case DEMO -> demo;
            };
        }
    }
}
