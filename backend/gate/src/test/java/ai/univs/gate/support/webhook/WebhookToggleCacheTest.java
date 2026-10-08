package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.shared.web.enums.CallerType;
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
        assertThat(cache.knownDisabled(1L, CallerType.API)).isFalse();
        assertThat(cache.knownDisabled(1L, CallerType.DEMO)).isFalse();
    }

    @Test
    @DisplayName("설정이 없는 프로젝트는 두 경로 모두 꺼짐으로 기억한다")
    void 설정_없음() {
        cache.remember(1L, null, cache.generation());

        assertThat(cache.knownDisabled(1L, CallerType.API)).isTrue();
        assertThat(cache.knownDisabled(1L, CallerType.DEMO)).isTrue();
        assertThat(cache.knownDisabled(2L, CallerType.API)).as("다른 프로젝트는 모른다").isFalse();
    }

    @Test
    @DisplayName("경로마다 따로 본다 — API 만 켜진 프로젝트는 데모만 꺼짐")
    void 경로별() {
        cache.remember(1L, config(false, true), cache.generation());

        assertThat(cache.knownDisabled(1L, CallerType.API)).isFalse();
        assertThat(cache.knownDisabled(1L, CallerType.DEMO)).isTrue();
    }

    @Test
    @DisplayName("TTL 이 지나면 잊는다 — 다른 인스턴스에서 바꾼 설정이 늦어도 이 시간 안에 반영된다")
    void 만료() {
        cache.remember(1L, null, cache.generation());

        nanos.addAndGet(WebhookToggleCache.TTL.toNanos() - 1);
        assertThat(cache.knownDisabled(1L, CallerType.API)).isTrue();

        nanos.addAndGet(1);
        assertThat(cache.knownDisabled(1L, CallerType.API)).isFalse();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 지우면 바로 잊는다")
    void 바로_지움() {
        cache.remember(1L, null, cache.generation());

        cache.evictAfterCommit(1L);

        assertThat(cache.knownDisabled(1L, CallerType.API)).isFalse();
    }

    @Test
    @DisplayName("트랜잭션 안에서 지우면 커밋 뒤에 잊는다 — 커밋 전에 지우면 전송 스레드가 옛 값을 다시 채운다")
    void 커밋_뒤에_지움() {
        cache.remember(1L, null, cache.generation());
        TransactionSynchronizationManager.initSynchronization();

        cache.evictAfterCommit(1L);
        assertThat(cache.knownDisabled(1L, CallerType.API)).as("커밋 전").isTrue();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.knownDisabled(1L, CallerType.API)).as("커밋 뒤").isFalse();
    }

    @Test
    @DisplayName("롤백되면 지우지 않는다 — 설정이 그대로이므로")
    void 롤백() {
        cache.remember(1L, null, cache.generation());
        TransactionSynchronizationManager.initSynchronization();

        cache.evictAfterCommit(1L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(cache.knownDisabled(1L, CallerType.API)).isTrue();
    }

    @Test
    @DisplayName("읽는 사이에 지워졌으면 읽은 값을 남기지 않는다 — 켠 직후의 이벤트를 낡은 「꺼짐」으로 버리지 않게")
    void 읽는_사이_변경() {
        long before = cache.generation();
        // 전송 스레드가 옛 값(설정 없음)을 읽는 사이 설정이 저장되고 커밋 뒤에 지워졌다
        cache.evict(1L);

        cache.remember(1L, null, before);

        assertThat(cache.knownDisabled(1L, CallerType.API)).isFalse();
    }
}
