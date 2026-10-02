package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookService;
import ai.univs.gate.support.webhook.WebhookTestResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 콘솔 「테스트 전송」 (UG-344). 저장된 URL 로 {@code TEST} 이벤트 한 건을 보내고 결과를 돌려준다.
 *
 * <p><b>트랜잭션을 열지 않는다.</b> 응답을 최대 약 9초 기다리는 동안 DB 커넥션을 쥐지 않게 한다. 설정은 짧게 읽고 놓는다.
 *
 * <p><b>남용을 막는다.</b> 요청 스레드가 응답을 기다리고, 웹훅과 같은 연결 풀을 쓴다. 그래서 두 겹으로 묶는다.
 * <ul>
 *   <li>같은 프로젝트는 {@link #MIN_INTERVAL} 안에 다시 보낼 수 없다 — 버튼 연타
 *   <li>전체 동시 실행은 {@link #MAX_CONCURRENT} 건 — 여러 프로젝트로 나눠 누르는 경우. 실제 웹훅 전송이 밀리지 않게 한다
 * </ul>
 * 넘치면 PJ-112(429)다. gate 는 환경당 단일 인스턴스 전제라 메모리 안의 기록으로 충분하다.
 */
@Slf4j
@Component
public class SendWebhookTestUseCase {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    static final int MAX_CONCURRENT = 4;
    /** 오래된 기록을 지우는 기준 — 간격보다 넉넉히 크게. */
    private static final int PRUNE_THRESHOLD = 1000;

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookService webhookService;
    private final ConcurrentHashMap<Long, Instant> lastSent = new ConcurrentHashMap<>();
    private final Semaphore inFlight = new Semaphore(MAX_CONCURRENT);
    private final Clock clock;

    @Autowired
    public SendWebhookTestUseCase(ProjectService projectService,
                                  WebhookConfigRepository webhookConfigRepository,
                                  WebhookService webhookService) {
        this(projectService, webhookConfigRepository, webhookService, Clock.systemUTC());
    }

    SendWebhookTestUseCase(ProjectService projectService,
                           WebhookConfigRepository webhookConfigRepository,
                           WebhookService webhookService,
                           Clock clock) {
        this.projectService = projectService;
        this.webhookConfigRepository = webhookConfigRepository;
        this.webhookService = webhookService;
        this.clock = clock;
    }

    public WebhookTestResult execute(Long projectId) {
        UserContext ctx = UserContext.get();
        projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());
        WebhookConfig config = webhookConfigRepository.findByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.WEBHOOK_CONFIG_NOT_FOUND));

        reserveInterval(projectId);
        if (!inFlight.tryAcquire()) {
            log.warn("Webhook test rejected (too many in flight): projectId={}", projectId);
            throw new CustomGateException(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
        }
        try {
            return webhookService.sendTest(projectId, config);
        } finally {
            inFlight.release();
        }
    }

    /** 간격 검사와 기록을 한 번에 한다 — 동시에 두 번 누르면 하나만 통과한다. */
    private void reserveInterval(Long projectId) {
        Instant now = Instant.now(clock);
        boolean[] allowed = {false};
        lastSent.compute(projectId, (id, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(MIN_INTERVAL))) {
                allowed[0] = true;
                return now;
            }
            return previous;
        });
        if (!allowed[0]) {
            throw new CustomGateException(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
        }
        if (lastSent.size() > PRUNE_THRESHOLD) {
            Instant cutoff = now.minus(MIN_INTERVAL);
            lastSent.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        }
    }
}
