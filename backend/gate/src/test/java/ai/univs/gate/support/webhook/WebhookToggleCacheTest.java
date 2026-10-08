package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.webhook.WebhookToggleCache.Decision;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("UG-361: 웹훅 토글 캐시")
class WebhookToggleCacheTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final WebhookToggleCache cache = new WebhookToggleCache();

    {
        cache.nanoTime = nanos::get;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static WebhookConfig config(boolean demo, boolean api) {
        return WebhookConfig.builder().webhookUrl("https://example.com/hook").demoEnabled(demo).apiEnabled(api).build();
    }

    @Test
    @DisplayName("모르면 꺼졌다고 하지 않는다 — 대기열에 넣어 전송 스레드가 DB 로 확인하게")
    void 모르면_넣는다() {
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.ENQUEUE);
        assertThat(cache.decide(1L, CallerType.DEMO)).isEqualTo(Decision.ENQUEUE);
    }

    @Test
    @DisplayName("설정이 없는 프로젝트는 두 경로 모두 꺼짐으로 기억한다")
    void 설정_없음() {
        cache.remember(1L, null, cache.generation());

        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.SKIP);
        assertThat(cache.decide(1L, CallerType.DEMO)).isEqualTo(Decision.SKIP);
        assertThat(cache.decide(2L, CallerType.API)).as("다른 프로젝트는 모른다").isEqualTo(Decision.ENQUEUE);
    }

    @Test
    @DisplayName("경로마다 따로 본다 — API 만 켜진 프로젝트는 데모만 꺼짐")
    void 경로별() {
        cache.remember(1L, config(false, true), cache.generation());

        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.ENQUEUE);
        assertThat(cache.decide(1L, CallerType.DEMO)).isEqualTo(Decision.SKIP);
    }

    @Test
    @DisplayName("반박 리뷰 M-1: 오래된 「꺼짐」은 한 건만 확인하러 보내고 나머지는 계속 건너뛴다")
    void 오래되면_한_건만_확인() {
        cache.remember(1L, null, cache.generation());

        nanos.addAndGet(WebhookToggleCache.FRESH.toNanos() - 1);
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.SKIP);

        nanos.addAndGet(1);
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.PROBE);
        assertThat(cache.decide(1L, CallerType.API)).as("확인이 돌아오기 전").isEqualTo(Decision.SKIP);
        assertThat(cache.decide(1L, CallerType.DEMO)).as("경로가 달라도 프로젝트당 한 건").isEqualTo(Decision.SKIP);

        cache.remember(1L, null, cache.generation());   // 확인이 돌아왔다
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.SKIP);
        nanos.addAndGet(WebhookToggleCache.FRESH.toNanos());
        assertThat(cache.decide(1L, CallerType.API)).as("다시 오래되면 또 한 건").isEqualTo(Decision.PROBE);
    }

    @Test
    @DisplayName("확인하러 보낸 건이 대기열에 못 들어가면 다음 요청이 다시 확인한다")
    void 확인_실패() {
        cache.remember(1L, null, cache.generation());
        nanos.addAndGet(WebhookToggleCache.FRESH.toNanos());
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.PROBE);

        cache.probeDropped(1L);

        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.PROBE);
    }

    @Test
    @DisplayName("확인이 끝내 돌아오지 않으면 MAX_AGE 에 잊는다 — 다른 인스턴스에서 켠 설정이 늦어도 이 안에 반영된다")
    void 최대_나이() {
        cache.remember(1L, null, cache.generation());
        nanos.addAndGet(WebhookToggleCache.FRESH.toNanos());
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.PROBE);

        nanos.addAndGet(WebhookToggleCache.MAX_AGE.toNanos() - WebhookToggleCache.FRESH.toNanos());

        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.ENQUEUE);
    }

    @Test
    @DisplayName("projectId 가 없으면 예전처럼 넣는다 — 요청 스레드에서 예외를 내지 않는다")
    void 프로젝트_없음() {
        assertThat(cache.decide(null, CallerType.API)).isEqualTo(Decision.ENQUEUE);
        cache.remember(null, null, cache.generation());
    }

    @Test
    @DisplayName("트랜잭션 밖에서 지우면 바로 잊는다")
    void 바로_지움() {
        cache.remember(1L, null, cache.generation());

        cache.evictAfterCommit(1L);

        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.ENQUEUE);
    }

    @Test
    @DisplayName("트랜잭션 안에서 지우면 커밋 뒤에 잊는다 — 커밋 전에 지우면 전송 스레드가 옛 값을 다시 채운다")
    void 커밋_뒤에_지움() {
        cache.remember(1L, null, cache.generation());
        TransactionSynchronizationManager.initSynchronization();

        cache.evictAfterCommit(1L);
        assertThat(cache.decide(1L, CallerType.API)).as("커밋 전").isEqualTo(Decision.SKIP);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.decide(1L, CallerType.API)).as("커밋 뒤").isEqualTo(Decision.ENQUEUE);
    }

    @Test
    @DisplayName("지운 자리표보다 앞서 읽은 값은 들어오지 못하고, 그 뒤에 읽은 값은 남는다")
    void 지우기_겹침() {
        cache.evict(1L);
        long newer = cache.generation();
        cache.remember(1L, null, newer - 1);      // 그보다 앞서 읽은 낡은 값은 들어오지 못한다
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.ENQUEUE);

        cache.remember(1L, null, newer);
        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.SKIP);
    }

    @Test
    @DisplayName("롤백되면 지우지 않는다 — 설정이 그대로이므로")
    void 롤백() {
        cache.remember(1L, null, cache.generation());
        TransactionSynchronizationManager.initSynchronization();

        cache.evictAfterCommit(1L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(cache.decide(1L, CallerType.API)).isEqualTo(Decision.SKIP);
    }

    @Test
    @DisplayName("읽는 사이에 지워졌으면 읽은 값을 남기지 않는다 — 켠 직후의 이벤트를 낡은 「꺼짐」으로 버리지 않게")
    void 읽는_사이_변경() {
        long before = cache.generation();
        // 전송 스레드가 옛 값(설정 없음)을 읽는 사이 설정이 저장되고 커밋 뒤에 지워졌다
        cache.evict(1L);

        cache.remember(1L, null, before);
        assertThat(cache.decide(1L, CallerType.API)).as("낡은 값은 지운 자리를 덮지 못한다").isEqualTo(Decision.ENQUEUE);

        cache.remember(1L, null, cache.generation());
        assertThat(cache.decide(1L, CallerType.API)).as("지운 뒤 새로 읽은 값은 남는다").isEqualTo(Decision.SKIP);
    }
}
