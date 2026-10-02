package ai.univs.gate.modules.webhook.application.usecase;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookService;
import ai.univs.gate.support.webhook.WebhookTestResult;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 콘솔 「테스트 전송」 (UG-344). 저장된 URL 로 {@code TEST} 이벤트 한 건을 보내고 결과를 돌려준다.
 *
 * <p><b>트랜잭션을 열지 않는다.</b> 응답을 최대 약 9초 기다리는 동안 DB 커넥션을 쥐지 않게 한다. 설정은 짧게 읽고 놓는다.
 *
 * <p><b>남용을 막는다.</b> 요청 스레드가 응답을 기다리고, 웹훅과 같은 연결 풀을 쓴다. 그래서 세 겹으로 묶는다.
 * <ul>
 *   <li><b>계정당 동시 1건</b> — 한 사용자가 응답 없는 URL 로 프로젝트를 여럿 돌려 전체 자리를 차지하지 못하게
 *       (반박 리뷰 W1: 프로젝트당 간격만으로는 프로젝트 2개로 전체 4자리를 계속 쥘 수 있었다)
 *   <li><b>프로젝트당 간격</b> — 직전 테스트가 <b>끝난</b> 뒤 {@link #MIN_INTERVAL}. 시작 기준이면 9초 걸리는 시도
 *       동안에도 3초마다 새로 시작할 수 있다
 *   <li><b>전체 동시 {@link #MAX_CONCURRENT} 건</b> — 여러 계정이 함께 누르는 경우. 실제 웹훅 전송이 밀리지 않게 한다
 * </ul>
 * 넘치면 PJ-112 다. 간격은 실제로 보낸 경우에만 기록한다 — 자리가 없어 거절된 요청이 다음 3초를 막지 않게.
 * 시각은 단조 시계로 잰다 — 벽시계가 뒤로 가도 막히지 않게. gate 는 환경당 단일 인스턴스 전제라 메모리 안의 기록으로 충분하다.
 */
@Slf4j
@Component
public class SendWebhookTestUseCase {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    static final int MAX_CONCURRENT = 4;
    /** 기록이 이만큼 쌓이면 간격이 지난 것을 지운다. 크기는 최근 간격 안에 테스트한 프로젝트 수를 넘지 않는다. */
    private static final int PRUNE_THRESHOLD = 1000;

    private final ProjectService projectService;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookService webhookService;
    /** 프로젝트별 직전 테스트가 끝난 시각 (단조 시계, 나노초). */
    private final ConcurrentHashMap<Long, Long> lastFinished = new ConcurrentHashMap<>();
    private final Set<Long> busyAccounts = ConcurrentHashMap.newKeySet();
    private final Semaphore inFlight = new Semaphore(MAX_CONCURRENT);
    private final LongSupplier nanoTime;

    @Autowired
    public SendWebhookTestUseCase(ProjectService projectService,
                                  WebhookConfigRepository webhookConfigRepository,
                                  WebhookService webhookService) {
        this(projectService, webhookConfigRepository, webhookService, System::nanoTime);
    }

    SendWebhookTestUseCase(ProjectService projectService,
                           WebhookConfigRepository webhookConfigRepository,
                           WebhookService webhookService,
                           LongSupplier nanoTime) {
        this.projectService = projectService;
        this.webhookConfigRepository = webhookConfigRepository;
        this.webhookService = webhookService;
        this.nanoTime = nanoTime;
    }

    public WebhookTestResult execute(Long projectId) {
        UserContext ctx = UserContext.get();
        Long accountId = ctx.getAccountIdAsLong();
        // 소유 확인이 먼저다 — 남의 프로젝트로 그 URL 에 서명된 요청을 보내게 하거나 남의 간격을 소모시키지 못하게
        projectService.validateOwnership(projectId, accountId);
        WebhookConfig config = webhookConfigRepository.findByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.WEBHOOK_CONFIG_NOT_FOUND));

        checkInterval(projectId);
        if (!busyAccounts.add(accountId)) {
            throw new CustomGateException(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
        }
        try {
            if (!inFlight.tryAcquire()) {
                log.warn("Webhook test rejected (too many in flight): projectId={}", projectId);
                throw new CustomGateException(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
            }
            try {
                return webhookService.sendTest(projectId, config);
            } finally {
                inFlight.release();
                recordFinished(projectId);
            }
        } finally {
            busyAccounts.remove(accountId);
        }
    }

    private void checkInterval(Long projectId) {
        Long finished = lastFinished.get(projectId);
        if (finished != null && nanoTime.getAsLong() - finished < MIN_INTERVAL.toNanos()) {
            throw new CustomGateException(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
        }
    }

    private void recordFinished(Long projectId) {
        long now = nanoTime.getAsLong();
        lastFinished.put(projectId, now);
        if (lastFinished.size() > PRUNE_THRESHOLD) {
            lastFinished.entrySet().removeIf(e -> now - e.getValue() >= MIN_INTERVAL.toNanos());
        }
    }
}
